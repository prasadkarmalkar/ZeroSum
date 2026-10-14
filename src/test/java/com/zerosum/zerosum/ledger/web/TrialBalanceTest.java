package com.zerosum.zerosum.ledger.web;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.account.seed.TreasurySeeder;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.service.AdminSeeder;
import com.zerosum.zerosum.auth.service.AuthService;
import com.zerosum.zerosum.auth.service.TokenService;
import com.zerosum.zerosum.auth.web.RegisterRequest;
import com.zerosum.zerosum.auth.web.User;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.service.LedgerService;
import com.zerosum.zerosum.ledger.service.TrialBalanceService;
import com.zerosum.zerosum.ledger.service.TrialBalanceService.TrialBalance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /admin/trial-balance end to end against zerosum_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TrialBalanceTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthService authService;

	@Autowired
	private AdminSeeder adminSeeder;

	@Autowired
	private TreasurySeeder treasurySeeder;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private LedgerService ledgerService;

	@Autowired
	private TrialBalanceService trialBalanceService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
	private final Logger logger = (Logger) LoggerFactory.getLogger(TrialBalanceService.class);

	private User admin;
	private User alice;
	private User bob;
	private Long treasury;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table ledger_entries, ledger_transactions, idempotency_keys restart identity");
		accountRepository.deleteAll();
		userRepository.deleteAll();
		treasurySeeder.seed();
		adminSeeder.seed();
		admin = userRepository.findByEmail("admin@zerosum.test").orElseThrow();
		alice = authService.register(new RegisterRequest("alice@example.com", "s3cret-Passw0rd"));
		bob = authService.register(new RegisterRequest("bob@example.com", "s3cret-Passw0rd"));
		treasury = accountRepository.findBySystemCode(Account.TREASURY).orElseThrow().getId();
		logs.start();
		logger.addAppender(logs);
	}

	@AfterEach
	void tearDown() {
		logger.detachAppender(logs);
	}

	// --- healthy ledger ---

	@Test
	void emptyLedgerIsBalanced() throws Exception {
		trialBalance(admin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balanced").value(true))
				.andExpect(jsonPath("$.entriesTotal").value("0.00"))
				.andExpect(jsonPath("$.balancesTotal").value("0.00"))
				.andExpect(jsonPath("$.mismatchedAccounts").value(0))
				.andExpect(jsonPath("$.transactionCount").value(0))
				.andExpect(jsonPath("$.checkedAt", notNullValue()));
	}

	@Test
	void ledgerStaysBalancedThroughDepositsAndTransfers() throws Exception {
		deposit(alice, 100000);
		deposit(bob, 25000);
		transfer(alice, bob, 30000);
		transfer(bob, alice, 5050);

		trialBalance(admin)
				.andExpect(jsonPath("$.balanced").value(true))
				.andExpect(jsonPath("$.entriesTotal").value("0.00"))
				.andExpect(jsonPath("$.balancesTotal").value("0.00"))
				.andExpect(jsonPath("$.mismatchedAccounts").value(0))
				.andExpect(jsonPath("$.transactionCount").value(4));
		assertThat(errorLogs()).isEmpty();
	}

	// --- broken ledger (only possible by going around the application) ---

	@Test
	void driftedCachedBalanceIsDetectedAndLoggedAtErrorLevel() throws Exception {
		deposit(alice, 100000);
		jdbcTemplate.update("update accounts set balance_minor = balance_minor + 1 where id = ?", accountOf(alice));

		trialBalance(admin)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.balanced").value(false))
				.andExpect(jsonPath("$.entriesTotal").value("0.00"))
				.andExpect(jsonPath("$.balancesTotal").value("0.01"))
				.andExpect(jsonPath("$.mismatchedAccounts").value(1));

		assertThat(errorLogs()).singleElement().satisfies(message -> assertThat(message)
				.contains("Trial balance FAILED")
				.contains("[" + accountOf(alice) + "]"));
	}

	@Test
	void unbalancedEntriesAreDetected() throws Exception {
		Long txId = deposit(alice, 100000);
		// A stray entry with no matching opposite: the entries no longer sum to zero
		jdbcTemplate.update(
				"insert into ledger_entries (transaction_id, account_id, amount_minor, currency) values (?, ?, 500, 'INR')",
				txId, accountOf(bob));

		trialBalance(admin)
				.andExpect(jsonPath("$.balanced").value(false))
				.andExpect(jsonPath("$.entriesTotal").value("5.00"))
				.andExpect(jsonPath("$.balancesTotal").value("0.00"))
				.andExpect(jsonPath("$.mismatchedAccounts").value(1));
	}

	@Test
	void entriesOnAnAccountThatNoLongerExistsCountAsMismatched() throws Exception {
		deposit(alice, 100000);
		jdbcTemplate.update("delete from accounts where id = ?", accountOf(alice));

		trialBalance(admin)
				.andExpect(jsonPath("$.balanced").value(false))
				.andExpect(jsonPath("$.entriesTotal").value("0.00"))
				.andExpect(jsonPath("$.balancesTotal").value("-1000.00"))
				.andExpect(jsonPath("$.mismatchedAccounts").value(1));
	}

	// --- one snapshot ---

	@Test
	void stayBalancedWhileTransfersCommitConcurrently() throws Exception {
		deposit(alice, 1_000_000);
		deposit(bob, 1_000_000);
		AtomicBoolean running = new AtomicBoolean(true);
		ExecutorService writer = Executors.newSingleThreadExecutor();

		// Transfers keep committing on another thread while the trial balance runs over and over
		Future<Integer> transfers = writer.submit(() -> {
			int count = 0;
			while (running.get()) {
				transfer(count % 2 == 0 ? alice : bob, count % 2 == 0 ? bob : alice, 1 + count % 7);
				count++;
			}
			return count;
		});

		List<TrialBalance> results = new ArrayList<>();
		try {
			for (int i = 0; i < 40; i++) {
				results.add(trialBalanceService.check());
			}
		} finally {
			running.set(false);
		}
		int transferCount = transfers.get(10, TimeUnit.SECONDS);
		writer.shutdown();

		assertThat(transferCount).isPositive();
		assertThat(results).allSatisfy(result -> {
			assertThat(result.balanced()).isTrue();
			assertThat(result.mismatchedAccounts()).isZero();
		});
		// Proof that writes really landed between checks, not before or after all of them
		assertThat(results.getFirst().transactionCount()).isLessThan(results.getLast().transactionCount());
	}

	// --- who may ask ---

	@Test
	void regularUserGets403() throws Exception {
		trialBalance(alice)
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"));
	}

	@Test
	void missingTokenGets401() throws Exception {
		mockMvc.perform(get("/admin/trial-balance"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void swaggerDocumentsItUnderAdmin() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/admin/trial-balance'].get.tags[0]").value("Admin"))
				.andExpect(jsonPath("$.paths['/admin/trial-balance'].get.responses['403']").exists());
	}

	// --- helpers ---

	private ResultActions trialBalance(User caller) throws Exception {
		return mockMvc.perform(get("/admin/trial-balance")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issueAccessToken(caller).value()));
	}

	private Long deposit(User user, long amountMinor) {
		return ledgerService.post(TransactionType.DEPOSIT, "Deposit", List.of(
				new EntryLine(treasury, Money.of(-amountMinor, "INR")),
				new EntryLine(accountOf(user), Money.of(amountMinor, "INR"))));
	}

	private void transfer(User from, User to, long amountMinor) {
		ledgerService.post(TransactionType.TRANSFER, "Transfer", List.of(
				new EntryLine(accountOf(from), Money.of(-amountMinor, "INR")),
				new EntryLine(accountOf(to), Money.of(amountMinor, "INR"))));
	}

	private Long accountOf(User user) {
		return accountRepository.findByUserId(user.getId()).orElseThrow().getId();
	}

	private List<String> errorLogs() {
		return logs.list.stream()
				.filter(event -> event.getLevel() == Level.ERROR)
				.map(ILoggingEvent::getFormattedMessage)
				.toList();
	}

}
