package com.zerosum.zerosum.account;

import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * If opening the account fails halfway through registration, the user must be rolled back too:
 * a user without an account must be impossible.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegistrationAtomicityTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@MockitoBean
	private AccountService accountService;

	@BeforeEach
	void cleanDatabase() {
		accountRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void failureWhileOpeningAccountRollsBackTheUser() throws Exception {
		// The user row has already been inserted and flushed by the time the listener runs
		when(accountService.openUserAccount(anyLong())).thenThrow(new IllegalStateException("simulated failure"));

		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "doomed@example.com", "password": "s3cret-Passw0rd"}
								"""))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"));

		assertThat(userRepository.findByEmail("doomed@example.com")).isEmpty();
		assertThat(userRepository.count()).isZero();
		assertThat(accountRepository.count()).isZero();
	}

}
