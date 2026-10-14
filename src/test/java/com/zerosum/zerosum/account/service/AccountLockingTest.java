package com.zerosum.zerosum.account.service;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.domain.AccountType;
import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Row locks against the real zerosum_test PostgreSQL database, using two threads that each run their
 * own transaction (and so their own connection). The test profile sets lock_timeout to 2s.
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountLockingTest {

	@Autowired
	private AccountService accountService;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private TransactionTemplate tx;

	private final ExecutorService threads = Executors.newFixedThreadPool(2);

	private Long a;
	private Long b;

	@BeforeEach
	void createAccounts() {
		accountRepository.deleteAll();
		a = accountRepository.saveAndFlush(Account.forUser(9001L, inr())).getId();
		b = accountRepository.saveAndFlush(Account.forUser(9002L, inr())).getId();
	}

	@Test
	void secondTransactionWaitsUntilTheFirstCommits() throws Exception {
		CountDownLatch firstHoldsLock = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);

		Future<?> first = threads.submit(() -> tx.executeWithoutResult(status -> {
			accountService.lockAccounts(List.of(a));
			firstHoldsLock.countDown();
			await(releaseFirst);
		}));
		assertThat(firstHoldsLock.await(5, TimeUnit.SECONDS)).isTrue();

		Future<List<AccountView>> second = threads.submit(() -> tx.execute(status -> accountService.lockAccounts(List.of(a))));

		// While the first transaction holds the row, the second is blocked on it
		assertThatThrownBy(() -> second.get(700, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

		releaseFirst.countDown();
		first.get(5, TimeUnit.SECONDS);
		assertThat(second.get(5, TimeUnit.SECONDS)).extracting(AccountView::id).containsExactly(a);
	}

	@Test
	void locksInAscendingIdOrderWhateverOrderIsGiven() {
		List<AccountView> views = tx.execute(status -> accountService.lockAccounts(List.of(b, a, b)));

		assertThat(views).extracting(AccountView::id).containsExactly(a, b);
		assertThat(views.getFirst().type()).isEqualTo(AccountType.USER);
		assertThat(views.getFirst().currency()).isEqualTo(inr());
		assertThat(views.getFirst().balance()).isEqualTo(Money.of(0, "INR"));
	}

	@Test
	void oppositeTransfersRunRepeatedlyWithoutDeadlocking() throws Exception {
		// A→B and B→A at the same time, many times over; each holds its locks briefly before committing.
		// Without a fixed lock order this deadlocks (Postgres aborts one with "deadlock detected").
		Runnable aToB = () -> lockAndHold(List.of(a, b));
		Runnable bToA = () -> lockAndHold(List.of(b, a));

		for (int round = 0; round < 15; round++) {
			CompletableFuture<Void> one = CompletableFuture.runAsync(aToB, threads);
			CompletableFuture<Void> two = CompletableFuture.runAsync(bToA, threads);
			CompletableFuture.allOf(one, two).get(10, TimeUnit.SECONDS); // throws if either failed
		}
	}

	@Test
	void stuckLockFailsFastWithTheLockTimeout() throws Exception {
		CountDownLatch firstHoldsLock = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		Future<?> first = threads.submit(() -> tx.executeWithoutResult(status -> {
			accountService.lockAccounts(List.of(a));
			firstHoldsLock.countDown();
			await(releaseFirst);
		}));
		assertThat(firstHoldsLock.await(5, TimeUnit.SECONDS)).isTrue();

		long start = System.nanoTime();
		try {
			assertThatThrownBy(() -> tx.execute(status -> accountService.lockAccounts(List.of(a))))
					.isInstanceOf(PessimisticLockingFailureException.class);
			Duration waited = Duration.ofNanos(System.nanoTime() - start);
			assertThat(waited).isBetween(Duration.ofMillis(1500), Duration.ofSeconds(4));
		} finally {
			releaseFirst.countDown();
			first.get(5, TimeUnit.SECONDS);
		}
	}

	@Test
	void refusesToRunOutsideATransaction() {
		assertThatThrownBy(() -> accountService.lockAccounts(List.of(a)))
				.isInstanceOf(IllegalTransactionStateException.class);
	}

	@Test
	void unknownAccountIsReported() {
		assertThatThrownBy(() -> tx.execute(status -> accountService.lockAccounts(List.of(a, 999_999L))))
				.isInstanceOf(AccountNotFoundException.class)
				.hasMessageContaining("999999");
	}

	private void lockAndHold(List<Long> ids) {
		tx.executeWithoutResult(status -> {
			accountService.lockAccounts(ids);
			sleep(20);
		});
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static Currency inr() {
		return Currency.getInstance("INR");
	}

}
