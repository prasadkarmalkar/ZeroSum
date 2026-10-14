package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.exception.LedgerInvariantViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Currency;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Full stack against zerosum_test: a valid post persists the transaction and every entry,
 * and an invalid one persists nothing at all. Counts are read with plain SQL, outside JPA.
 */
@SpringBootTest
@ActiveProfiles("test")
class LedgerServiceIntegrationTest {

	@Autowired
	private LedgerService ledgerService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private AccountRepository accountRepository;

	private Long from;
	private Long to;

	@BeforeEach
	void emptyLedgerAndCreateAccounts() {
		// Plain SQL: the ledger is append-only, so JPA can't delete it
		jdbcTemplate.execute("truncate table ledger_entries, ledger_transactions restart identity");
		accountRepository.deleteAll();
		from = accountRepository.saveAndFlush(Account.forUser(8001L, Currency.getInstance("INR"))).getId();
		to = accountRepository.saveAndFlush(Account.forUser(8002L, Currency.getInstance("INR"))).getId();
	}

	@Test
	void validPostPersistsTheTransactionAndAllItsEntries() {
		Long id = ledgerService.post(TransactionType.TRANSFER, "Rent", List.of(
				new EntryLine(from, Money.of(-500, "INR")),
				new EntryLine(to, Money.of(500, "INR"))));

		Map<String, Object> tx = jdbcTemplate.queryForMap(
				"select type, description, created_at from ledger_transactions where id = ?", id);
		assertThat(tx.get("type")).isEqualTo("TRANSFER");
		assertThat(tx.get("description")).isEqualTo("Rent");
		assertThat(tx.get("created_at")).isNotNull();

		List<Map<String, Object>> entries = jdbcTemplate.queryForList(
				"select account_id, amount_minor, currency from ledger_entries where transaction_id = ? order by id", id);
		assertThat(entries).containsExactly(
				Map.of("account_id", from, "amount_minor", -500L, "currency", "INR"),
				Map.of("account_id", to, "amount_minor", 500L, "currency", "INR"));

		Long sum = jdbcTemplate.queryForObject(
				"select sum(amount_minor) from ledger_entries where transaction_id = ?", Long.class, id);
		assertThat(sum).isZero();
	}

	@Test
	void invalidPostPersistsNothing() {
		assertThatThrownBy(() -> ledgerService.post(TransactionType.TRANSFER, "Bad", List.of(
				new EntryLine(from, Money.of(-500, "INR")),
				new EntryLine(to, Money.of(499, "INR")))))
				.isInstanceOf(LedgerInvariantViolationException.class);

		assertThat(count("ledger_transactions")).isZero();
		assertThat(count("ledger_entries")).isZero();
	}

	@Test
	void postToAnUnknownAccountPersistsNothing() {
		assertThatThrownBy(() -> ledgerService.post(TransactionType.TRANSFER, "To nowhere", List.of(
				new EntryLine(from, Money.of(-500, "INR")),
				new EntryLine(999_999L, Money.of(500, "INR")))))
				.isInstanceOf(AccountNotFoundException.class);

		assertThat(count("ledger_transactions")).isZero();
		assertThat(count("ledger_entries")).isZero();
	}

	private long count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
	}

}
