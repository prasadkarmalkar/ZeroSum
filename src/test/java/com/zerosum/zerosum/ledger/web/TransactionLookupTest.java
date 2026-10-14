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
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.service.LedgerService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /accounts/me/transactions/{id}: the client picks the id, so ownership is checked on every request. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransactionLookupTest {

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
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private User alice;
	private User bob;
	private User carol;
	private Long transferId;
	private Long depositId;

	@BeforeEach
	void setUp() {
		jdbcTemplate.execute("truncate table ledger_entries, ledger_transactions, idempotency_keys restart identity");
		accountRepository.deleteAll();
		userRepository.deleteAll();
		treasurySeeder.seed();
		alice = authService.register(new RegisterRequest("alice@example.com", "s3cret-Passw0rd"));
		bob = authService.register(new RegisterRequest("bob@example.com", "s3cret-Passw0rd"));
		carol = authService.register(new RegisterRequest("carol@example.com", "s3cret-Passw0rd"));

		Long treasury = accountRepository.findBySystemCode(Account.TREASURY).orElseThrow().getId();
		depositId = ledgerService.post(TransactionType.DEPOSIT, "Welcome bonus", List.of(
				new EntryLine(treasury, Money.of(-100000, "INR")),
				new EntryLine(accountOf(alice), Money.of(100000, "INR"))));
		transferId = ledgerService.post(TransactionType.TRANSFER, "Dinner", List.of(
				new EntryLine(accountOf(alice), Money.of(-20000, "INR")),
				new EntryLine(accountOf(bob), Money.of(20000, "INR"))));
	}

	// --- sender or recipient: 200 with their own side ---

	@Test
	void senderSeesTheTransactionWithTheirNegativeAmount() throws Exception {
		lookup(alice, transferId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(transferId))
				.andExpect(jsonPath("$.type").value("TRANSFER"))
				.andExpect(jsonPath("$.description").value("Dinner"))
				.andExpect(jsonPath("$.createdAt", notNullValue()))
				.andExpect(jsonPath("$.amount").value("-200.00"))
				// Only the caller's side: nothing about the other party's account
				.andExpect(jsonPath("$.length()").value(5));
	}

	@Test
	void recipientSeesTheSameTransactionWithTheirPositiveAmount() throws Exception {
		lookup(bob, transferId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(transferId))
				.andExpect(jsonPath("$.amount").value("200.00"));
	}

	@Test
	void depositRecipientSeesTheDeposit() throws Exception {
		lookup(alice, depositId)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value("DEPOSIT"))
				.andExpect(jsonPath("$.amount").value("1000.00"));
	}

	// --- anyone else: 404, indistinguishable from a missing id ---

	@Test
	void anotherUserGets404() throws Exception {
		lookup(carol, transferId)
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("TRANSACTION_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("Transaction " + transferId + " was not found"));
	}

	@Test
	void someoneElsesTransactionLooksExactlyLikeAMissingOne() throws Exception {
		long missingId = 999_999L;
		String othersBody = body(lookup(carol, transferId).andExpect(status().isNotFound()));
		String missingBody = body(lookup(carol, missingId).andExpect(status().isNotFound()));

		// Byte-for-byte the same once the requested id is swapped: nothing hints that the transaction exists
		assertThat(othersBody.replace(transferId.toString(), "{id}"))
				.isEqualTo(missingBody.replace(Long.toString(missingId), "{id}"));
	}

	@Test
	void beingAnAdminDoesNotGrantAccessToOthersTransactions() throws Exception {
		adminSeeder.seed();
		User admin = userRepository.findByEmail("admin@zerosum.test").orElseThrow();

		lookup(admin, depositId).andExpect(status().isNotFound());
		lookup(admin, transferId).andExpect(status().isNotFound());
	}

	@Test
	void nonExistentIdGets404() throws Exception {
		lookup(alice, 999_999L)
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.errorCode").value("TRANSACTION_NOT_FOUND"));
	}

	// --- 400 / 401 ---

	@Test
	void idInTheWrongFormatGets400() throws Exception {
		for (String id : List.of("abc", "1.5", "99999999999999999999", "0", "-3")) {
			mockMvc.perform(get("/accounts/me/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer(alice)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
					.andExpect(jsonPath("$.errors[0].field").value("id"));
		}
	}

	@Test
	void noTokenGets401() throws Exception {
		mockMvc.perform(get("/accounts/me/transactions/" + transferId))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void swaggerDocumentsTheEndpoint() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/accounts/me/transactions/{id}'].get.tags[0]").value("Accounts"))
				.andExpect(jsonPath("$.paths['/accounts/me/transactions/{id}'].get.responses['404']").exists())
				.andExpect(jsonPath("$.paths['/accounts/me/transactions/{id}'].get.responses['400']").exists());
	}

	// --- helpers ---

	private ResultActions lookup(User caller, Long id) throws Exception {
		return mockMvc.perform(get("/accounts/me/transactions/" + id).header(HttpHeaders.AUTHORIZATION, bearer(caller)));
	}

	private static String body(ResultActions result) throws Exception {
		return result.andReturn().getResponse().getContentAsString();
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.issueAccessToken(user).value();
	}

	private Long accountOf(User user) {
		return accountRepository.findByUserId(user.getId()).orElseThrow().getId();
	}

}
