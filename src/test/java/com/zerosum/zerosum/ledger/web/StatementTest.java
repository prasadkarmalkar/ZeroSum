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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /accounts/me/statement end to end against zerosum_test. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StatementTest {

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

	@Autowired
	private JsonMapper jsonMapper;

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
	}

	@Test
	void newestFirstWithRunningBalanceLikeTheExample() throws Exception {
		deposit(alice, 70000);           // +700  ->  700
		deposit(bob, 50000);
		transfer(bob, alice, 30000);     // +300  -> 1000
		transfer(alice, bob, 20000);     // -200  ->  800

		statement(alice, "page=0&size=3")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[*].type", contains("TRANSFER", "TRANSFER", "DEPOSIT")))
				.andExpect(jsonPath("$.content[*].amount", contains("-200.00", "300.00", "700.00")))
				.andExpect(jsonPath("$.content[*].balanceAfter", contains("800.00", "1000.00", "700.00")))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(3))
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.totalPages").value(1));
	}

	@Test
	void newestBalanceAfterEqualsTheCurrentBalance() throws Exception {
		deposit(alice, 70000);
		transfer(alice, bob, 12345);
		deposit(alice, 999);

		String newest = firstRow(alice).get("balanceAfter").asString();
		String current = jsonMapper.readTree(mockMvc.perform(get("/accounts/me")
						.header(HttpHeaders.AUTHORIZATION, bearer(alice)))
				.andReturn().getResponse().getContentAsString()).get("balance").asString();

		assertThat(newest).isEqualTo(current).isEqualTo("586.54");
	}

	@Test
	void pagesSplitTheHistoryAndKeepEveryRowsRunningBalance() throws Exception {
		for (int i = 1; i <= 5; i++) {
			deposit(alice, i * 100L); // +1.00, +2.00, ... +5.00
		}

		statement(alice, "page=0&size=2")
				.andExpect(jsonPath("$.content[*].balanceAfter", contains("15.00", "10.00")))
				.andExpect(jsonPath("$.totalElements").value(5))
				.andExpect(jsonPath("$.totalPages").value(3));
		statement(alice, "page=1&size=2")
				.andExpect(jsonPath("$.content[*].balanceAfter", contains("6.00", "3.00")));
		statement(alice, "page=2&size=2")
				.andExpect(jsonPath("$.content[*].balanceAfter", contains("1.00")));
		statement(alice, "page=3&size=2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty());
	}

	@Test
	void rowsWithTheSameTimestampKeepAStableOrderAcrossPages() throws Exception {
		for (int i = 1; i <= 6; i++) {
			deposit(alice, i * 100L);
		}
		// Make every transaction share one timestamp; order must still be fixed, by entry id
		jdbcTemplate.update("update ledger_transactions set created_at = timestamp with time zone '2026-01-01 10:00:00+00'");

		List<String> seen = new ArrayList<>();
		for (int page = 0; page < 3; page++) {
			for (JsonNode row : rows(alice, "page=" + page + "&size=2")) {
				seen.add(row.get("amount").asString() + "->" + row.get("balanceAfter").asString());
			}
		}

		assertThat(seen).containsExactly(
				"6.00->21.00", "5.00->15.00", "4.00->10.00", "3.00->6.00", "2.00->3.00", "1.00->1.00");
		// And asking again gives the very same order
		assertThat(rows(alice, "page=0&size=2").get(0).get("amount").asString()).isEqualTo("6.00");
	}

	@Test
	void showsOnlyTheCallersOwnEntries() throws Exception {
		deposit(alice, 10000);
		deposit(bob, 20000);
		transfer(bob, alice, 5000);

		statement(alice, "")
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[*].amount", contains("50.00", "100.00")));
		statement(bob, "")
				.andExpect(jsonPath("$.totalElements").value(2))
				.andExpect(jsonPath("$.content[*].amount", contains("-50.00", "200.00")));
	}

	@Test
	void emptyStatementHasNoPages() throws Exception {
		statement(alice, "")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content").isEmpty())
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.totalElements").value(0))
				.andExpect(jsonPath("$.totalPages").value(0));
	}

	@Test
	void pageSizeIsCappedAt100() throws Exception {
		statement(alice, "size=5000")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100));
	}

	@Test
	void invalidPagingIsRejectedWith400() throws Exception {
		statement(alice, "page=-1")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("page"));
		statement(alice, "size=0")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("size"));
	}

	@Test
	void requiresAToken() throws Exception {
		mockMvc.perform(get("/accounts/me/statement"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void swaggerDocumentsItUnderAccountsWithItsOwnPageShape() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(jsonPath("$.paths['/accounts/me/statement'].get.tags[0]").value("Accounts"))
				.andExpect(jsonPath("$.paths['/accounts/me/statement'].get.parameters[*].name", contains("page", "size")));
	}

	// --- helpers ---

	private ResultActions statement(User user, String query) throws Exception {
		return mockMvc.perform(get("/accounts/me/statement" + (query.isEmpty() ? "" : "?" + query))
				.header(HttpHeaders.AUTHORIZATION, bearer(user)));
	}

	private JsonNode rows(User user, String query) throws Exception {
		return jsonMapper.readTree(statement(user, query).andReturn().getResponse().getContentAsString()).get("content");
	}

	private JsonNode firstRow(User user) throws Exception {
		return rows(user, "size=1").get(0);
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.issueAccessToken(user).value();
	}

	private void deposit(User user, long amountMinor) {
		Long treasury = accountRepository.findBySystemCode(Account.TREASURY).orElseThrow().getId();
		ledgerService.post(TransactionType.DEPOSIT, "Deposit", List.of(
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

}
