package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.exception.InsufficientFundsException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Overdraft protection and cached balances, end to end against zerosum_test.
 * Balances are read back with plain SQL, outside JPA.
 */
@SpringBootTest
@ActiveProfiles("test")
class LedgerBalanceIntegrationTest {

	private static final Currency INR = Currency.getInstance("INR");

	@Autowired
	private LedgerService ledgerService;

	@Autowired
	private AccountService accountService;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final ExecutorService threads = Executors.newFixedThreadPool(2);

	private Long treasury;
	private Long alice;
	private Long bob;
	private Long carol;

	@BeforeEach
	void createAccounts() {
		jdbcTemplate.execute("truncate table ledger_entries, ledger_transactions restart identity");
		accountRepository.deleteAll();
		treasury = accountRepository.saveAndFlush(Account.system(Account.TREASURY, INR)).getId();
		alice = accountRepository.saveAndFlush(Account.forUser(7001L, INR)).getId();
		bob = accountRepository.saveAndFlush(Account.forUser(7002L, INR)).getId();
		carol = accountRepository.saveAndFlush(Account.forUser(7003L, INR)).getId();
	}

	@AfterEach
	void stopThreads() {
		threads.shutdownNow();
	}

	@Test
	void depositMovesMoneyOutOfTreasuryWhichMayGoNegative() {
		deposit(alice, 100000);

		assertThat(balance(alice)).isEqualTo(100000);
		assertThat(balance(treasury)).isEqualTo(-100000);
	}

	@Test
	void transferUpdatesBothCachedBalances() {
		deposit(alice, 100000);

		transfer(alice, bob, 30000);

		assertThat(balance(alice)).isEqualTo(70000);
		assertThat(balance(bob)).isEqualTo(30000);
	}

	@Test
	void transferringTheWholeBalanceLeavesExactlyZero() {
		deposit(alice, 50000);

		transfer(alice, bob, 50000);

		assertThat(balance(alice)).isZero();
		assertThat(balance(bob)).isEqualTo(50000);
	}

	@Test
	void overdraftIsRejectedAndChangesNothing() {
		deposit(alice, 50000);
		long transactionsBefore = count("ledger_transactions");
		long entriesBefore = count("ledger_entries");

		assertThatThrownBy(() -> transfer(alice, bob, 50001))
				.isInstanceOf(InsufficientFundsException.class)
				.hasMessage("Account " + alice + " has 500.00 INR available but 500.01 INR was requested")
				.satisfies(e -> assertThat(((InsufficientFundsException) e).getStatus().value()).isEqualTo(422));

		assertThat(balance(alice)).isEqualTo(50000);
		assertThat(balance(bob)).isZero();
		assertThat(count("ledger_transactions")).isEqualTo(transactionsBefore);
		assertThat(count("ledger_entries")).isEqualTo(entriesBefore);
	}

	@Test
	void userWithNoMoneyCannotSendAny() {
		assertThatThrownBy(() -> transfer(bob, alice, 1)).isInstanceOf(InsufficientFundsException.class);
	}

	@Test
	void everyCachedBalanceEqualsTheSumOfItsEntries() {
		deposit(alice, 100000);
		deposit(bob, 20000);
		transfer(alice, bob, 25000);
		transfer(bob, carol, 40000);
		transfer(carol, alice, 15000);
		assertThatThrownBy(() -> transfer(carol, bob, 99999)).isInstanceOf(InsufficientFundsException.class);

		List<Map<String, Object>> drift = jdbcTemplate.queryForList("""
				select a.id, a.balance_minor, coalesce(sum(e.amount_minor), 0) as entries_sum
				from accounts a left join ledger_entries e on e.account_id = a.id
				group by a.id, a.balance_minor
				having a.balance_minor <> coalesce(sum(e.amount_minor), 0)
				""");
		assertThat(drift).isEmpty();

		// Zero-sum: all balances together, treasury included, add up to nothing
		assertThat(jdbcTemplate.queryForObject("select sum(balance_minor) from accounts", Long.class)).isZero();
		assertThat(balance(alice)).isEqualTo(90000);
		assertThat(balance(bob)).isEqualTo(5000);
		assertThat(balance(carol)).isEqualTo(25000);
	}

	@Test
	void twoConcurrentTransfersCannotBothSpendTheSameMoney() throws Exception {
		deposit(alice, 100000);
		CountDownLatch start = new CountDownLatch(1);

		// Each wants 600 of Alice's 1000; started together, only one can win
		Future<Long> toBob = threads.submit(atSignal(start, () -> transfer(alice, bob, 60000)));
		Future<Long> toCarol = threads.submit(atSignal(start, () -> transfer(alice, carol, 60000)));
		start.countDown();

		List<Throwable> failures = new ArrayList<>();
		int successes = 0;
		for (Future<Long> result : List.of(toBob, toCarol)) {
			try {
				result.get(10, TimeUnit.SECONDS);
				successes++;
			} catch (ExecutionException e) {
				failures.add(e.getCause());
			}
		}

		assertThat(successes).isEqualTo(1);
		assertThat(failures).singleElement().isInstanceOf(InsufficientFundsException.class);
		assertThat(balance(alice)).isEqualTo(40000);
		assertThat(balance(bob) + balance(carol)).isEqualTo(60000);
	}

	@Test
	void applyBalanceChangesRefusesToRunOutsideATransaction() {
		assertThatThrownBy(() -> accountService.applyBalanceChanges(Map.of(alice, Money.of(100, "INR"))))
				.isInstanceOf(IllegalTransactionStateException.class);
		assertThat(balance(alice)).isZero();
	}

	private Long deposit(Long account, long amountMinor) {
		return ledgerService.post(TransactionType.DEPOSIT, "Deposit", List.of(
				new EntryLine(treasury, Money.of(-amountMinor, "INR")),
				new EntryLine(account, Money.of(amountMinor, "INR"))));
	}

	private Long transfer(Long from, Long to, long amountMinor) {
		return ledgerService.post(TransactionType.TRANSFER, "Transfer", List.of(
				new EntryLine(from, Money.of(-amountMinor, "INR")),
				new EntryLine(to, Money.of(amountMinor, "INR"))));
	}

	private long balance(Long accountId) {
		return jdbcTemplate.queryForObject("select balance_minor from accounts where id = ?", Long.class, accountId);
	}

	private long count(String table) {
		return jdbcTemplate.queryForObject("select count(*) from " + table, Long.class);
	}

	private static <T> Callable<T> atSignal(CountDownLatch start, Callable<T> task) {
		return () -> {
			start.await();
			return task.call();
		};
	}

}
