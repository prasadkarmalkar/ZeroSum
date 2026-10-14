package com.zerosum.zerosum.account;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.domain.AccountType;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.web.User;
import com.zerosum.zerosum.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registering through the API commits the user and their account together. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenAccountOnRegistrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private AccountRepository accountRepository;

	@BeforeEach
	void cleanDatabase() {
		accountRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void registeringOpensOneZeroBalanceAccountInTheProjectCurrency() throws Exception {
		mockMvc.perform(post("/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "new@example.com", "password": "s3cret-Passw0rd"}
								"""))
				.andExpect(status().isCreated());

		User user = userRepository.findByEmail("new@example.com").orElseThrow();
		Account account = accountRepository.findByUserId(user.getId()).orElseThrow();

		assertThat(accountRepository.count()).isEqualTo(1);
		assertThat(account.getType()).isEqualTo(AccountType.USER);
		assertThat(account.getBalance()).isEqualTo(Money.of(0, "INR"));
	}

}
