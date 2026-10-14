package com.zerosum.zerosum.ledger.web;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.account.seed.TreasurySeeder;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.service.AdminSeeder;
import com.zerosum.zerosum.auth.service.AuthService;
import com.zerosum.zerosum.auth.service.TokenService;
import com.zerosum.zerosum.auth.web.RegisterRequest;
import com.zerosum.zerosum.auth.web.User;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /admin/deposits end to end against zerosum_test, including the ADMIN-only rule. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminDepositTest {

	private static final String TO_ALICE = """
			{"recipientEmail": "alice@example.com", "amount": "500.00", "description": "Welcome bonus"}
			""";

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
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private User admin;
	private User alice;
	private User bob;

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
	}

	// --- 201 ---

	@Test
	void adminDepositFundsTheUserOutOfTreasury() throws Exception {
		deposit(admin, "dep-1", TO_ALICE)
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "false"))
				.andExpect(jsonPath("$.transactionId", notNullValue()))
				.andExpect(jsonPath("$.amount").value("500.00"))
				.andExpect(jsonPath("$.recipientEmail").value("alice@example.com"))
				.andExpect(jsonPath("$.createdAt", notNullValue()));

		assertThat(balanceOf(alice)).isEqualTo(50000);
		assertThat(treasuryBalance()).isEqualTo(-50000);
		assertThat(jdbcTemplate.queryForMap("select type, description from ledger_transactions"))
				.isEqualTo(Map.of("type", "DEPOSIT", "description", "Welcome bonus"));
	}

	@Test
	void treasuryGoesNegativeByExactlyTheTotalHandedOut() throws Exception {
		deposit(admin, "dep-1", TO_ALICE).andExpect(status().isCreated());
		deposit(admin, "dep-2", """
				{"recipientEmail": "bob@example.com", "amount": "120.25"}
				""").andExpect(status().isCreated());

		assertThat(treasuryBalance()).isEqualTo(-(50000 + 12025));
		assertThat(jdbcTemplate.queryForObject("select sum(balance_minor) from accounts", Long.class)).isZero();
		assertThat(jdbcTemplate.queryForObject(
				"select description from ledger_transactions where id = 2", String.class)).isEqualTo("Deposit");
	}

	@Test
	void fundedUserCanThenTransfer() throws Exception {
		deposit(admin, "dep-1", TO_ALICE).andExpect(status().isCreated());

		mockMvc.perform(post("/transfers")
						.header(HttpHeaders.AUTHORIZATION, bearer(alice))
						.header("Idempotency-Key", "tr-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"recipientEmail": "bob@example.com", "amount": "200.00"}
								"""))
				.andExpect(status().isCreated());

		assertThat(balanceOf(alice)).isEqualTo(30000);
		assertThat(balanceOf(bob)).isEqualTo(20000);
		assertThat(treasuryBalance()).isEqualTo(-50000);
	}

	// --- who may deposit ---

	@Test
	void regularUserGets403AndNothingChanges() throws Exception {
		deposit(alice, "dep-1", TO_ALICE)
				.andExpect(status().isForbidden())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
				.andExpect(jsonPath("$.instance").value("/admin/deposits"));

		assertThat(balanceOf(alice)).isZero();
		assertThat(treasuryBalance()).isZero();
	}

	@Test
	void missingTokenGets401() throws Exception {
		mockMvc.perform(post("/admin/deposits")
						.header("Idempotency-Key", "dep-1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(TO_ALICE))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	// --- 400 / 404 ---

	@Test
	void invalidRequestOrMissingKeyGets400() throws Exception {
		deposit(admin, "dep-1", """
				{"recipientEmail": "alice@example.com", "amount": "-5"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("amount"));

		mockMvc.perform(post("/admin/deposits")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.contentType(MediaType.APPLICATION_JSON)
						.content(TO_ALICE))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("idempotencyKey"));

		assertThat(treasuryBalance()).isZero();
	}

	@Test
	void unknownRecipientGets404AndNothingChanges() throws Exception {
		deposit(admin, "dep-1", """
				{"recipientEmail": "nobody@example.com", "amount": "10.00"}
				""")
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));

		assertThat(treasuryBalance()).isZero();
		assertThat(jdbcTemplate.queryForObject("select count(*) from idempotency_keys", Long.class)).isZero();
	}

	// --- idempotency (shared with transfers) ---

	@Test
	void repeatedKeyReplaysAndDepositsOnce() throws Exception {
		deposit(admin, "dep-1", TO_ALICE).andExpect(status().isCreated());
		deposit(admin, "dep-1", TO_ALICE)
				.andExpect(status().isCreated())
				.andExpect(header().string("Idempotent-Replayed", "true"));

		assertThat(balanceOf(alice)).isEqualTo(50000);
		assertThat(treasuryBalance()).isEqualTo(-50000);
	}

	@Test
	void repeatedKeyWithADifferentBodyGets409() throws Exception {
		deposit(admin, "dep-1", TO_ALICE).andExpect(status().isCreated());
		deposit(admin, "dep-1", """
				{"recipientEmail": "alice@example.com", "amount": "9999.00", "description": "Welcome bonus"}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));

		assertThat(balanceOf(alice)).isEqualTo(50000);
	}

	@Test
	void aKeyUsedForATransferCannotBeReusedForADeposit() throws Exception {
		deposit(admin, "dep-1", """
				{"recipientEmail": "admin@zerosum.test", "amount": "50.00"}
				""").andExpect(status().isCreated());
		mockMvc.perform(post("/transfers")
						.header(HttpHeaders.AUTHORIZATION, bearer(admin))
						.header("Idempotency-Key", "shared")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"recipientEmail": "alice@example.com", "amount": "10.00"}
								"""))
				.andExpect(status().isCreated());

		// Same admin, same key, same email/amount/description shape, but a different operation
		deposit(admin, "shared", """
				{"recipientEmail": "alice@example.com", "amount": "10.00", "description": "Transfer"}
				""")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_REUSED"));
	}

	// --- Swagger ---

	@Test
	void swaggerListsTheEndpointUnderTheAdminTag() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/admin/deposits'].post.tags[0]").value("Admin"))
				.andExpect(jsonPath("$.paths['/admin/deposits'].post.responses['403']").exists())
				.andExpect(jsonPath("$.paths['/admin/deposits'].post.parameters[?(@.name == 'Idempotency-Key')].required").value(true));
	}

	// --- helpers ---

	private ResultActions deposit(User caller, String key, String json) throws Exception {
		return mockMvc.perform(post("/admin/deposits")
				.header(HttpHeaders.AUTHORIZATION, bearer(caller))
				.header("Idempotency-Key", key)
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.issueAccessToken(user).value();
	}

	private long balanceOf(User user) {
		return jdbcTemplate.queryForObject("select balance_minor from accounts where user_id = ?", Long.class, user.getId());
	}

	private long treasuryBalance() {
		return jdbcTemplate.queryForObject(
				"select balance_minor from accounts where system_code = ?", Long.class, Account.TREASURY);
	}

}
