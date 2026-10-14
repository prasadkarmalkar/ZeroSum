package com.zerosum.zerosum.ledger.repository;

import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.domain.Entry;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.LedgerTransaction;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against the real zerosum_test PostgreSQL database, so the CHECK and index are Postgres's own. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class LedgerTransactionRepositoryTest {

	@Autowired
	private LedgerTransactionRepository repository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void savesTransactionWithEntriesAndLoadsThemBack() {
		LedgerTransaction saved = repository.saveAndFlush(transfer(10L, 20L, 500));
		entityManager.clear(); // read back from the database, not the persistence context

		LedgerTransaction loaded = repository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getType()).isEqualTo(TransactionType.TRANSFER);
		assertThat(loaded.getDescription()).isEqualTo("Transfer");
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(loaded.getEntries()).extracting(Entry::getAccountId).containsExactly(10L, 20L);
		assertThat(loaded.getEntries()).extracting(Entry::getAmount)
				.containsExactly(Money.of(-500, "INR"), Money.of(500, "INR"));
		assertThat(loaded.getEntries()).allSatisfy(entry -> {
			assertThat(entry.getId()).isNotNull();
			assertThat(entry.getTransaction().getId()).isEqualTo(saved.getId());
		});
	}

	@Test
	void hibernateIgnoresChangesToALoadedTransactionAndItsEntries() {
		Long id = repository.saveAndFlush(transfer(10L, 20L, 500)).getId();
		entityManager.clear();

		// Tamper with managed entities behind the API's back, then flush
		LedgerTransaction loaded = repository.findById(id).orElseThrow();
		ReflectionTestUtils.setField(loaded, "description", "Tampered");
		ReflectionTestUtils.setField(loaded.getEntries().getFirst(), "amountMinor", -999_999L);
		entityManager.flush();
		entityManager.clear();

		LedgerTransaction reloaded = repository.findById(id).orElseThrow();
		assertThat(reloaded.getDescription()).isEqualTo("Transfer");
		assertThat(reloaded.getEntries().getFirst().getAmount()).isEqualTo(Money.of(-500, "INR"));
	}

	@Test
	void transactionsAndEntriesCannotBeDeleted() {
		Long id = repository.saveAndFlush(transfer(10L, 20L, 500)).getId();
		LedgerTransaction loaded = repository.findById(id).orElseThrow();

		assertThatThrownBy(() -> entityManager.remove(loaded))
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessageContaining("append-only");
		assertThatThrownBy(() -> entityManager.remove(loaded.getEntries().getFirst()))
				.isInstanceOf(UnsupportedOperationException.class)
				.hasMessageContaining("append-only");
	}

	@Test
	void repositoryExposesNoDeleteOrUpdateMethods() {
		List<String> methods = Arrays.stream(LedgerTransactionRepository.class.getMethods())
				.map(Method::getName)
				.toList();

		assertThat(methods).containsExactlyInAnyOrder("save", "saveAndFlush", "findById", "count");
	}

	@Test
	void databaseRejectsZeroAmountEntry() {
		Long txId = repository.saveAndFlush(transfer(10L, 20L, 500)).getId();

		// Bypass the factory's own check to prove the CHECK constraint is the last line of defence
		assertThatThrownBy(() -> jdbcTemplate.update(
				"insert into ledger_entries (transaction_id, account_id, amount_minor, currency) values (?, 10, 0, 'INR')",
				txId))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_ledger_entries_amount_non_zero");
	}

	@Test
	void entryAccountIdIsIndexed() {
		String indexDef = jdbcTemplate.queryForObject(
				"select indexdef from pg_indexes where tablename = 'ledger_entries' and indexname = 'idx_ledger_entries_account_id'",
				String.class);

		assertThat(indexDef).contains("(account_id)");
	}

	private static LedgerTransaction transfer(Long from, Long to, long amountMinor) {
		return LedgerTransaction.create(TransactionType.TRANSFER, "Transfer", List.of(
				new EntryLine(from, Money.of(-amountMinor, "INR")),
				new EntryLine(to, Money.of(amountMinor, "INR"))));
	}

}
