package com.zerosum.zerosum.concurrency;

import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.account.seed.TreasurySeeder;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.web.User;
import com.zerosum.zerosum.concurrency.ConcurrentRunner.Outcome;
import com.zerosum.zerosum.ledger.exception.InsufficientFundsException;
import com.zerosum.zerosum.ledger.service.IdempotentResult;
import com.zerosum.zerosum.ledger.service.TransferService;
import com.zerosum.zerosum.ledger.service.TrialBalanceService;
import com.zerosum.zerosum.ledger.service.TrialBalanceService.TrialBalance;
import com.zerosum.zerosum.ledger.web.TransferRequest;
import com.zerosum.zerosum.ledger.web.TransferResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The README's claim, under load: money is never created or lost, even with requests in parallel.
 * <p>
 * Deliberately <b>no</b> test-level transaction: every transfer commits (or rolls back) on its own
 * connection, exactly as in production. Requests go through {@link TransferService}, the same code the
 * controller calls, with its locking, overdraft check and idempotency. Each test ends with the trial balance.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestUsers.class)
class ConcurrencyTest {

	private static final Duration TIMEOUT = Duration.ofSeconds(60);

	@Autowired
	private TestUsers testUsers;

	@Autowired
	private TransferService transferService;

	@Autowired
	private TrialBalanceService trialBalanceService;

	@Autowired
	private TreasurySeeder treasurySeeder;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void emptySystem() {
		jdbcTemplate.execute("truncate table ledger_entries, ledger_transactions, idempotency_keys restart identity");
		accountRepository.deleteAll();
		userRepository.deleteAll();
		treasurySeeder.seed();
	}

	@AfterEach
	void ledgerIsStillWhole() {
		TrialBalance trialBalance = trialBalanceService.check();
		assertThat(trialBalance.balanced())
				.as("trial balance after the test: %s", trialBalance)
				.isTrue();
	}

	@Test
	void transferStorm() throws Exception {
		List<User> users = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			users.add(testUsers.createFunded("storm" + i + "@example.com", "1000.00"));
		}
		long usersTotalBefore = usersTotal();

		// 150 random transfers between random pairs, 1.00 to 300.00 each, all released at once.
		// Some will legitimately run out of money; that must be a clean InsufficientFunds, nothing else.
		Random random = new Random(42);
		List<Callable<IdempotentResult<TransferResponse>>> tasks = new ArrayList<>();
		for (int i = 0; i < 150; i++) {
			User from = users.get(random.nextInt(users.size()));
			User to = users.get(random.nextInt(users.size()));
			while (to == from) {
				to = users.get(random.nextInt(users.size()));
			}
			tasks.add(transfer(from, to, (1 + random.nextInt(300)) + ".00"));
		}

		List<Outcome<IdempotentResult<TransferResponse>>> outcomes = ConcurrentRunner.runAllAtOnce(tasks, TIMEOUT);

		assertThat(outcomes)
				.as("every transfer either succeeds or fails with InsufficientFunds")
				.allSatisfy(outcome -> {
					if (!outcome.succeeded()) {
						assertThat(outcome.error()).isInstanceOf(InsufficientFundsException.class);
					}
				});
		assertThat(outcomes).filteredOn(Outcome::succeeded).hasSizeGreaterThan(100);
		assertThat(usersTotal()).as("money only moved between users").isEqualTo(usersTotalBefore);
		assertThat(lowestUserBalance()).as("no user balance went negative").isGreaterThanOrEqualTo(0);
		assertThat(transferCount()).isEqualTo(outcomes.stream().filter(Outcome::succeeded).count());
	}

	@Test
	void overdraftRace() throws Exception {
		User alice = testUsers.createFunded("alice@example.com", "100.00");
		User bob = testUsers.createFunded("bob@example.com", "0");

		List<Callable<IdempotentResult<TransferResponse>>> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			tasks.add(transfer(alice, bob, "60.00"));
		}

		List<Outcome<IdempotentResult<TransferResponse>>> outcomes = ConcurrentRunner.runAllAtOnce(tasks, TIMEOUT);

		assertThat(outcomes).filteredOn(Outcome::succeeded).hasSize(1);
		assertThat(outcomes).filteredOn(outcome -> !outcome.succeeded())
				.hasSize(9)
				.allSatisfy(outcome -> assertThat(outcome.error()).isInstanceOf(InsufficientFundsException.class));
		assertThat(balanceOf(alice)).isEqualTo(4000);
		assertThat(balanceOf(bob)).isEqualTo(6000);
	}

	@Test
	void oppositeDirections() throws Exception {
		User a = testUsers.createFunded("a@example.com", "1000.00");
		User b = testUsers.createFunded("b@example.com", "1000.00");

		// 50 A→B of 5.00 and 50 B→A of 3.00, interleaved and released together: without a fixed
		// lock order this is the classic deadlock (T1 holds A wants B, T2 holds B wants A)
		List<Callable<IdempotentResult<TransferResponse>>> tasks = new ArrayList<>();
		for (int i = 0; i < 50; i++) {
			tasks.add(transfer(a, b, "5.00"));
			tasks.add(transfer(b, a, "3.00"));
		}

		List<Outcome<IdempotentResult<TransferResponse>>> outcomes = ConcurrentRunner.runAllAtOnce(tasks, TIMEOUT);

		assertThat(outcomes)
				.as("all 100 transfers complete: no deadlock, no lock timeout, no overdraft")
				.allSatisfy(outcome -> assertThat(outcome.error()).isNull());
		// A: 1000 - 50 × 5 + 50 × 3 = 900; B: 1000 + 50 × 5 - 50 × 3 = 1100
		assertThat(balanceOf(a)).isEqualTo(90000);
		assertThat(balanceOf(b)).isEqualTo(110000);
		assertThat(transferCount()).isEqualTo(100);
	}

	@Test
	void duplicateRequests() throws Exception {
		User alice = testUsers.createFunded("alice@example.com", "1000.00");
		User bob = testUsers.createFunded("bob@example.com", "0");
		String key = UUID.randomUUID().toString();
		TransferRequest request = new TransferRequest("bob@example.com", "100.00", "Rent");

		List<Callable<IdempotentResult<TransferResponse>>> tasks = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			tasks.add(() -> transferService.transfer(alice.getId(), key, request));
		}

		List<Outcome<IdempotentResult<TransferResponse>>> outcomes = ConcurrentRunner.runAllAtOnce(tasks, TIMEOUT);

		assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.error()).isNull());
		List<Long> transactionIds = outcomes.stream().map(o -> o.value().response().transactionId()).distinct().toList();
		assertThat(transactionIds).as("every response carries the same transaction id").hasSize(1);
		assertThat(outcomes).filteredOn(o -> !o.value().replayed()).hasSize(1);
		assertThat(transferCount()).isEqualTo(1);
		assertThat(balanceOf(alice)).isEqualTo(90000);
		assertThat(balanceOf(bob)).isEqualTo(10000);
	}

	// --- helpers ---

	private Callable<IdempotentResult<TransferResponse>> transfer(User from, User to, String amount) {
		TransferRequest request = new TransferRequest(to.getEmail(), amount, null);
		String key = UUID.randomUUID().toString();
		return () -> transferService.transfer(from.getId(), key, request);
	}

	private long balanceOf(User user) {
		return jdbcTemplate.queryForObject("select balance_minor from accounts where user_id = ?", Long.class, user.getId());
	}

	/** Money held by user accounts only (TREASURY and other SYSTEM accounts excluded). */
	private long usersTotal() {
		return jdbcTemplate.queryForObject("select coalesce(sum(balance_minor), 0) from accounts where type = 'USER'", Long.class);
	}

	private long lowestUserBalance() {
		return jdbcTemplate.queryForObject("select min(balance_minor) from accounts where type = 'USER'", Long.class);
	}

	private long transferCount() {
		return jdbcTemplate.queryForObject("select count(*) from ledger_transactions where type = 'TRANSFER'", Long.class);
	}

}
