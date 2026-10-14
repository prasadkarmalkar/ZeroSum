package com.zerosum.zerosum.ledger.web;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.account.seed.TreasurySeeder;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.service.AuthService;
import com.zerosum.zerosum.auth.service.TokenService;
import com.zerosum.zerosum.auth.web.RegisterRequest;
import com.zerosum.zerosum.auth.web.User;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.exception.IdempotencyKeyReusedException;
import com.zerosum.zerosum.ledger.service.LedgerService;
import com.zerosum.zerosum.ledger.service.TransferService;
import com.zerosum.zerosum.ledger.service.IdempotentResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Idempotency-Key on POST /transfers, end to end against zerosum_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferIdempotencyTest {

	private static final String TO_BOB = """
			{"recipientEmail": "bob@example.com", "amount": "100.00", "description": "Rent"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthService authService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private LedgerService ledgerService;

	@Autowired
	private TransferService transferService;

	@Autowired
	private TreasurySeeder treasurySeeder;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private JsonMapper jsonMapper;

	private final ExecutorService threads = Executors.newFixedThreadPool(2);

	private User alice;
	private User bob;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table ledger_entries, ledger_transactions, idempotency_keys restart identity");
		accountRepository.deleteAll();
		userRepository.deleteAll();
		treasurySeeder.seed();
		alice = authService.register(new RegisterRequest("alice@example.com", "s3cret-Passw0rd"));
		bob = authService.register(new RegisterRequest("bob@example.com", "s3cret-Passw0rd"));
		deposit(alice, 100000); // 1000.00 INR
	}

	@AfterEach
	void stopThreads() {
		threads.shutdownNow();
	}

	// --- replay ---

	@Test
	void sameKeyAndBodyReplaysTheOriginalResponseAndMovesMoneyOnce() throws Exception {
		String first = transfer(alice, "key-1", TO_BOB)
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "false"))
				.andReturn().getResponse().getContentAsString();

		String second = transfer(alice, "key-1", TO_BOB)
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "true"))
				.andReturn().getResponse().getContentAsString();

		assertThat(jsonMapper.readTree(second)).isEqualTo(jsonMapper.readTree(first));
		assertThat(balanceOf(alice)).isEqualTo(90000);
		assertThat(balanceOf(bob)).isEqualTo(10000);
		assertThat(transferCount()).isEqualTo(1);
	}

	@Test
	void equivalentBodiesCountAsTheSameRequest() throws Exception {
		transfer(alice, "key-1", """
				{"recipientEmail": "bob@example.com", "amount": "100.00"}
				""").andExpect(status().isCreated());

		// Different spelling, same meaning: email case, "100" vs "100.00", missing vs default description
		transfer(alice, "key-1", """
				{"recipientEmail": "BOB@Example.com", "amount": "100", "description": "Transfer"}
				""")
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "true"));

		assertThat(transferCount()).isEqualTo(1);
	}

	// --- reject ---

	@Test
	void sameKeyWithADifferentBodyIsRejectedWith409() throws Exception {
		transfer(alice, "key-1", TO_BOB).andExpect(status().isCreated());

		transfer(alice, "key-1", """
				{"recipientEmail": "bob@example.com", "amount": "999.00", "description": "Rent"}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"))
				.andExpect(jsonPath("$.detail").value("Idempotency key 'key-1' was already used for a different request"));

		assertThat(balanceOf(alice)).isEqualTo(90000);
		assertThat(transferCount()).isEqualTo(1);
	}

	// --- process ---

	@Test
	void differentKeysAreDifferentTransfers() throws Exception {
		transfer(alice, "key-1", TO_BOB).andExpect(status().isCreated());
		transfer(alice, "key-2", TO_BOB)
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "false"));

		assertThat(balanceOf(alice)).isEqualTo(80000);
		assertThat(transferCount()).isEqualTo(2);
	}

	@Test
	void keysAreScopedPerUser() throws Exception {
		deposit(bob, 50000);

		transfer(alice, "shared-key", TO_BOB).andExpect(status().isCreated());
		transfer(bob, "shared-key", """
				{"recipientEmail": "alice@example.com", "amount": "100.00", "description": "Rent"}
				""")
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "false"));

		assertThat(transferCount()).isEqualTo(2);
	}

	@Test
	void failedRequestIsNotRememberedSoTheKeyCanBeRetried() throws Exception {
		String tooMuch = """
				{"recipientEmail": "bob@example.com", "amount": "1500.00"}
				""";
		transfer(alice, "key-1", tooMuch).andExpect(status().isUnprocessableContent());
		assertThat(keyCount()).isZero();

		deposit(alice, 50000); // now 1500.00

		transfer(alice, "key-1", tooMuch)
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "false"));
		assertThat(balanceOf(alice)).isZero();
	}

	// --- the header ---

	@Test
	void missingOrInvalidKeyIsRejectedWith400() throws Exception {
		mockMvc.perform(post("/transfers")
						.header(HttpHeaders.AUTHORIZATION, bearer(alice))
						.contentType(MediaType.APPLICATION_JSON)
						.content(TO_BOB))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("idempotencyKey"));

		transfer(alice, "   ", TO_BOB).andExpect(status().isBadRequest());
		transfer(alice, "k".repeat(256), TO_BOB).andExpect(status().isBadRequest());

		assertThat(transferCount()).isZero();
	}

	@Test
	void swaggerDocumentsTheRequiredHeader() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/transfers'].post.parameters[?(@.name == 'Idempotency-Key')].in").value("header"))
				.andExpect(jsonPath("$.paths['/transfers'].post.parameters[?(@.name == 'Idempotency-Key')].required").value(true))
				.andExpect(jsonPath("$.paths['/transfers'].post.responses['409']").exists())
				.andExpect(jsonPath("$.paths['/transfers'].post.responses['201'].headers['Idempotent-Replayed']").exists());
	}

	// --- races ---

	@Test
	void twoIdenticalRequestsRacingMoveMoneyOnceAndBothGetTheSameResponse() throws Exception {
		for (int round = 0; round < 10; round++) {
			String key = "race-" + round;
			TransferRequest request = new TransferRequest("bob@example.com", "10.00", "Race " + round);

			List<IdempotentResult<TransferResponse>> results = race(
					() -> transferService.transfer(alice.getId(), key, request),
					() -> transferService.transfer(alice.getId(), key, request));

			assertThat(results).hasSize(2);
			assertThat(results.get(0).response()).isEqualTo(results.get(1).response());
			assertThat(results).extracting(IdempotentResult::replayed).containsExactlyInAnyOrder(false, true);
		}

		assertThat(transferCount()).isEqualTo(10);
		assertThat(keyCount()).isEqualTo(10);
		assertThat(balanceOf(alice)).isEqualTo(100000 - 10 * 1000);
	}

	@Test
	void twoDifferentRequestsRacingOnOneKeyProcessOneAndRejectTheOther() throws Exception {
		TransferRequest ten = new TransferRequest("bob@example.com", "10.00", null);
		TransferRequest twenty = new TransferRequest("bob@example.com", "20.00", null);

		List<Object> outcomes = raceAllowingFailure(
				() -> transferService.transfer(alice.getId(), "contested", ten),
				() -> transferService.transfer(alice.getId(), "contested", twenty));

		assertThat(outcomes).filteredOn(IdempotentResult.class::isInstance).hasSize(1);
		assertThat(outcomes).filteredOn(IdempotencyKeyReusedException.class::isInstance).hasSize(1);
		assertThat(transferCount()).isEqualTo(1);
	}

	// --- helpers ---

	private ResultActions transfer(User sender, String key, String json) throws Exception {
		return mockMvc.perform(post("/transfers")
				.header(HttpHeaders.AUTHORIZATION, bearer(sender))
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.issueAccessToken(user).value();
	}

	@SafeVarargs
	private List<IdempotentResult<TransferResponse>> race(Callable<IdempotentResult<TransferResponse>>... tasks) throws Exception {
		List<IdempotentResult<TransferResponse>> results = new ArrayList<>();
		for (Object outcome : raceAllowingFailure(tasks)) {
			if (outcome instanceof Throwable t) {
				throw new AssertionError("A racing request failed", t);
			}
			results.add((IdempotentResult<TransferResponse>) outcome);
		}
		return results;
	}

	/** Starts all tasks at the same moment; each outcome is its result or the exception it threw. */
	@SafeVarargs
	private List<Object> raceAllowingFailure(Callable<IdempotentResult<TransferResponse>>... tasks) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Future<IdempotentResult<TransferResponse>>> futures = new ArrayList<>();
		for (Callable<IdempotentResult<TransferResponse>> task : tasks) {
			futures.add(threads.submit(() -> {
				start.await();
				return task.call();
			}));
		}
		start.countDown();

		List<Object> outcomes = new ArrayList<>();
		for (Future<IdempotentResult<TransferResponse>> future : futures) {
			try {
				outcomes.add(future.get(10, TimeUnit.SECONDS));
			} catch (ExecutionException e) {
				outcomes.add(e.getCause());
			}
		}
		return outcomes;
	}

	private void deposit(User user, long amountMinor) {
		Long treasury = accountRepository.findBySystemCode(Account.TREASURY).orElseThrow().getId();
		Long account = accountRepository.findByUserId(user.getId()).orElseThrow().getId();
		ledgerService.post(TransactionType.DEPOSIT, "Test deposit", List.of(
				new EntryLine(treasury, Money.of(-amountMinor, "INR")),
				new EntryLine(account, Money.of(amountMinor, "INR"))));
	}

	private long balanceOf(User user) {
		return jdbcTemplate.queryForObject("select balance_minor from accounts where user_id = ?", Long.class, user.getId());
	}

	private long transferCount() {
		return jdbcTemplate.queryForObject("select count(*) from ledger_transactions where type = 'TRANSFER'", Long.class);
	}

	private long keyCount() {
		return jdbcTemplate.queryForObject("select count(*) from idempotency_keys", Long.class);
	}

}
