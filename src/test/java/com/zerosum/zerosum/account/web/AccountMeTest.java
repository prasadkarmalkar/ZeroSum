package com.zerosum.zerosum.account.web;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.service.AuthService;
import com.zerosum.zerosum.auth.service.TokenService;
import com.zerosum.zerosum.auth.web.RegisterRequest;
import com.zerosum.zerosum.auth.web.User;
import com.zerosum.zerosum.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccountMeTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthService authService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	private User alice;
	private User bob;

	@BeforeEach
	void registerUsers() {
		accountRepository.deleteAll();
		userRepository.deleteAll();
		alice = authService.register(new RegisterRequest("alice@example.com", "s3cret-Passw0rd"));
		bob = authService.register(new RegisterRequest("bob@example.com", "s3cret-Passw0rd"));
	}

	@Test
	void returnsTheCallersAccountWithZeroBalance() throws Exception {
		Account account = accountRepository.findByUserId(alice.getId()).orElseThrow();

		getMyAccount(alice)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(account.getId()))
				.andExpect(jsonPath("$.currency").value("INR"))
				.andExpect(jsonPath("$.balance").value("0.00"))
				// Only what a user needs: no owner id, type, system code or timestamps
				.andExpect(jsonPath("$.length()").value(3));
	}

	@Test
	void balanceIsADecimalStringNotANumber() throws Exception {
		Account account = accountRepository.findByUserId(alice.getId()).orElseThrow();
		account.applyChange(Money.of(105000, "INR"));
		accountRepository.saveAndFlush(account);

		getMyAccount(alice)
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("\"balance\":\"1050.00\"")));
	}

	@Test
	void eachUserSeesOnlyTheirOwnAccount() throws Exception {
		Long aliceAccountId = accountRepository.findByUserId(alice.getId()).orElseThrow().getId();
		Long bobAccountId = accountRepository.findByUserId(bob.getId()).orElseThrow().getId();

		getMyAccount(alice).andExpect(jsonPath("$.id").value(aliceAccountId));
		getMyAccount(bob).andExpect(jsonPath("$.id").value(bobAccountId));
	}

	@Test
	void identityComesFromTheTokenNotTheRequest() throws Exception {
		Long aliceAccountId = accountRepository.findByUserId(alice.getId()).orElseThrow().getId();

		// Bob's id smuggled in as query parameters is ignored; Alice still gets her own account
		mockMvc.perform(get("/accounts/me")
						.param("userId", bob.getId().toString())
						.param("id", bob.getId().toString())
						.header(HttpHeaders.AUTHORIZATION, bearer(alice)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(aliceAccountId));
	}

	@Test
	void missingOrInvalidTokenReturns401() throws Exception {
		mockMvc.perform(get("/accounts/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
		mockMvc.perform(get("/accounts/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	@Test
	void userWithoutAnAccountGets404() throws Exception {
		accountRepository.delete(accountRepository.findByUserId(alice.getId()).orElseThrow());

		getMyAccount(alice)
				.andExpect(status().isNotFound())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("ACCOUNT_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("User " + alice.getId() + " has no account"));
	}

	@Test
	void openApiTagsTheEndpointAndHidesTheUserIdParameter() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.paths['/accounts/me'].get.tags[0]").value("Accounts"))
				.andExpect(jsonPath("$.paths['/accounts/me'].get.parameters").doesNotExist())
				.andExpect(jsonPath("$.paths['/accounts/me'].get.responses['404']").exists())
				.andExpect(content().string(not(containsString("\"userId\""))));
	}

	private ResultActions getMyAccount(User user) throws Exception {
		return mockMvc.perform(get("/accounts/me").header(HttpHeaders.AUTHORIZATION, bearer(user)));
	}

	private String bearer(User user) {
		return "Bearer " + tokenService.issueAccessToken(user).value();
	}

}
