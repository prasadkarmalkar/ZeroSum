package com.zerosum.zerosum.account.repository;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.domain.AccountType;
import com.zerosum.zerosum.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against the real zerosum_test PostgreSQL database, so constraints are enforced by Postgres. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountRepositoryTest {

	private static final Currency INR = Currency.getInstance("INR");

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TestEntityManager entityManager;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void startEmpty() {
		// Other tests register users, which now opens accounts in the shared test database
		accountRepository.deleteAllInBatch();
	}

	@Test
	void savesAndFindsUserAccountByUserId() {
		Account saved = accountRepository.saveAndFlush(Account.forUser(1L, INR));
		entityManager.clear(); // read back from the database, not the persistence context

		Account found = accountRepository.findByUserId(1L).orElseThrow();
		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getType()).isEqualTo(AccountType.USER);
		assertThat(found.getSystemCode()).isNull();
		assertThat(found.getCurrency()).isEqualTo(INR);
		assertThat(found.getBalance()).isEqualTo(Money.zero(INR));
		assertThat(found.getCreatedAt()).isNotNull();
		assertThat(accountRepository.findByUserId(999L)).isEmpty();
	}

	@Test
	void savesAndFindsSystemAccountBySystemCode() {
		Account saved = accountRepository.saveAndFlush(Account.system("TREASURY", INR));
		entityManager.clear();

		Account found = accountRepository.findBySystemCode("TREASURY").orElseThrow();
		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getType()).isEqualTo(AccountType.SYSTEM);
		assertThat(found.getUserId()).isNull();
		assertThat(accountRepository.findBySystemCode("NOPE")).isEmpty();
	}

	@Test
	void persistsBalanceChanges() {
		Account account = accountRepository.saveAndFlush(Account.forUser(2L, INR));
		account.applyChange(Money.of(1050, "INR"));
		accountRepository.saveAndFlush(account);
		entityManager.clear();

		assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
				.isEqualTo(Money.of(1050, "INR"));
	}

	@Test
	void systemAccountBalanceMayBeNegative() {
		Account treasury = accountRepository.saveAndFlush(Account.system("TREASURY", INR));
		treasury.applyChange(Money.of(-5000, "INR"));
		accountRepository.saveAndFlush(treasury);
		entityManager.clear();

		assertThat(accountRepository.findBySystemCode("TREASURY").orElseThrow().getBalance())
				.isEqualTo(Money.of(-5000, "INR"));
	}

	@Test
	void databaseRejectsNegativeUserBalance() {
		Account account = accountRepository.saveAndFlush(Account.forUser(3L, INR));

		// Bypass the entity's own guard to prove the CHECK constraint is the last line of defence
		assertThatThrownBy(() -> jdbcTemplate.update(
				"update accounts set balance_minor = -1 where id = ?", account.getId()))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_accounts_user_balance_non_negative");
	}

	@Test
	void databaseRejectsSecondAccountForSameUser() {
		accountRepository.saveAndFlush(Account.forUser(4L, INR));

		assertThatThrownBy(() -> accountRepository.saveAndFlush(Account.forUser(4L, INR)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_accounts_user_id");
	}

	@Test
	void databaseRejectsDuplicateSystemCode() {
		accountRepository.saveAndFlush(Account.system("TREASURY", INR));

		assertThatThrownBy(() -> accountRepository.saveAndFlush(Account.system("TREASURY", INR)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_accounts_system_code");
	}

	@Test
	void databaseRejectsAccountWhoseOwnerDoesNotMatchItsType() {
		assertThatThrownBy(() -> jdbcTemplate.update(
				"insert into accounts (type, user_id, system_code, currency, balance_minor, created_at)"
						+ " values ('USER', null, 'TREASURY', 'INR', 0, now())"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ck_accounts_owner_matches_type");
	}

}
