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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /transfers end to end: security, validation, accounts, ledger and balances against zerosum_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TransferControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthService authService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private LedgerService ledgerService;

	@Autowired
	private TreasurySeeder treasurySeeder;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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

	// --- 201 ---

	@Test
	void transferMovesMoneyAndReturns201() throws Exception {
		transfer(alice, """
				{"recipientEmail": "bob@example.com", "amount": "250.50", "description": "Dinner"}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.transactionId", notNullValue()))
				.andExpect(jsonPath("$.amount").value("250.50"))
				.andExpect(jsonPath("$.recipientEmail").value("bob@example.com"))
				.andExpect(jsonPath("$.createdAt", notNullValue()));

		assertThat(balanceOf(alice)).isEqualTo(74950);
		assertThat(balanceOf(bob)).isEqualTo(25050);

		Map<String, Object> tx = jdbcTemplate.queryForMap(
				"select type, description from ledger_transactions where type = 'TRANSFER'");
		assertThat(tx).containsEntry("description", "Dinner");
		assertThat(jdbcTemplate.queryForList(
				"select e.account_id, e.amount_minor from ledger_entries e join ledger_transactions t on t.id = e.transaction_id"
						+ " where t.type = 'TRANSFER' order by e.id"))
				.containsExactly(
						Map.of("account_id", accountIdOf(alice), "amount_minor", -25050L),
						Map.of("account_id", accountIdOf(bob), "amount_minor", 25050L));
	}

	@Test
	void recipientEmailIsMatchedIgnoringCaseAndDescriptionDefaults() throws Exception {
		transfer(alice, """
				{"recipientEmail": "BOB@Example.com", "amount": "10"}
				""")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.amount").value("10.00"))
				.andExpect(jsonPath("$.recipientEmail").value("bob@example.com"));

		assertThat(jdbcTemplate.queryForObject(
				"select description from ledger_transactions where type = 'TRANSFER'", String.class)).isEqualTo("Transfer");
	}

	@Test
	void senderComesFromTheTokenAndASmuggledSenderFieldIsIgnored() throws Exception {
		deposit(bob, 50000);

		// Alice's token, but the body claims Bob is sending: the money still leaves Alice's account
		transfer(alice, """
				{"recipientEmail": "bob@example.com", "amount": "100.00",
				 "senderEmail": "bob@example.com", "senderId": %d, "fromAccountId": %d}
				""".formatted(bob.getId(), accountIdOf(bob)))
				.andExpect(status().isCreated());

		assertThat(balanceOf(alice)).isEqualTo(90000);
		assertThat(balanceOf(bob)).isEqualTo(60000);
	}

	// --- 400 ---

	@Test
	void invalidRequestReturns400WithEachField() throws Exception {
		transfer(alice, """
				{"recipientEmail": "not-an-email", "amount": "abc"}
				""")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("recipientEmail", "amount", "amount")));
	}

	@Test
	void amountMustBePositiveWithAtMostTwoDecimals() throws Exception {
		for (String amount : List.of("0", "0.00", "-5", "1.005", "", "1e3")) {
			transfer(alice, """
					{"recipientEmail": "bob@example.com", "amount": "%s"}
					""".formatted(amount))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.errors[0].field").value("amount"));
		}
		assertThat(balanceOf(alice)).isEqualTo(100000);
	}

	// --- 401 ---

	@Test
	void missingTokenReturns401() throws Exception {
		mockMvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content("""
						{"recipientEmail": "bob@example.com", "amount": "1.00"}
						"""))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	// --- 404 ---

	@Test
	void unknownRecipientReturns404AndChangesNothing() throws Exception {
		transfer(alice, """
				{"recipientEmail": "nobody@example.com", "amount": "1.00"}
				""")
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"));

		assertThat(balanceOf(alice)).isEqualTo(100000);
		assertThat(transferCount()).isZero();
	}

	// --- 422 ---

	@Test
	void sendingToYourselfReturns422() throws Exception {
		transfer(alice, """
				{"recipientEmail": "Alice@Example.com", "amount": "1.00"}
				""")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errorCode").value("SELF_TRANSFER_NOT_ALLOWED"));

		assertThat(transferCount()).isZero();
	}

	@Test
	void insufficientFundsReturns422AndChangesNothing() throws Exception {
		transfer(alice, """
				{"recipientEmail": "bob@example.com", "amount": "1000.01"}
				""")
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"))
				.andExpect(jsonPath("$.detail").value(
						"Account " + accountIdOf(alice) + " has 1000.00 INR available but 1000.01 INR was requested"));

		assertThat(balanceOf(alice)).isEqualTo(100000);
		assertThat(balanceOf(bob)).isZero();
		assertThat(transferCount()).isZero();
	}

	// --- helpers ---

	private ResultActions transfer(User sender, String json) throws Exception {
		return mockMvc.perform(post("/transfers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenService.issueAccessToken(sender).value())
				.header("Idempotency-Key", UUID.randomUUID().toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json));
	}

	private void deposit(User user, long amountMinor) {
		Long treasury = accountRepository.findBySystemCode(Account.TREASURY).orElseThrow().getId();
		ledgerService.post(TransactionType.DEPOSIT, "Test deposit", List.of(
				new EntryLine(treasury, Money.of(-amountMinor, "INR")),
				new EntryLine(accountIdOf(user), Money.of(amountMinor, "INR"))));
	}

	private Long accountIdOf(User user) {
		return accountRepository.findByUserId(user.getId()).orElseThrow().getId();
	}

	private long balanceOf(User user) {
		return jdbcTemplate.queryForObject(
				"select balance_minor from accounts where user_id = ?", Long.class, user.getId());
	}

	private long transferCount() {
		return jdbcTemplate.queryForObject(
				"select count(*) from ledger_transactions where type = 'TRANSFER'", Long.class);
	}

}
