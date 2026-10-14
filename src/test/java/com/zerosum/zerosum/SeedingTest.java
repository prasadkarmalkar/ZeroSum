package com.zerosum.zerosum;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.domain.AccountType;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.account.seed.TreasurySeeder;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.service.AdminSeeder;
import com.zerosum.zerosum.auth.web.Role;
import com.zerosum.zerosum.auth.web.User;
import com.zerosum.zerosum.common.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Startup seeding: TREASURY and the admin user from config. The seeders are run again explicitly here,
 * on an empty database, so the test doesn't depend on what other test classes left behind.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SeedingTest {

	@Autowired
	private TreasurySeeder treasurySeeder;

	@Autowired
	private AdminSeeder adminSeeder;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JsonMapper jsonMapper;

	@BeforeEach
	void emptyDatabase() {
		accountRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void seedsTreasuryAndAdminExactlyOnceAcrossRepeatedRuns() {
		for (int run = 0; run < 2; run++) {
			treasurySeeder.seed();
			adminSeeder.seed();
		}

		Account treasury = accountRepository.findBySystemCode(Account.TREASURY).orElseThrow();
		assertThat(treasury.getType()).isEqualTo(AccountType.SYSTEM);
		assertThat(treasury.getUserId()).isNull();
		assertThat(treasury.getBalance()).isEqualTo(Money.of(0, "INR"));

		User admin = userRepository.findByEmail("admin@zerosum.test").orElseThrow();
		assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
		assertThat(passwordEncoder.matches("admin-test-Passw0rd", admin.getPasswordHash())).isTrue();

		// Exactly one of each; the admin also got an account, like every registered user
		assertThat(userRepository.count()).isEqualTo(1);
		assertThat(accountRepository.count()).isEqualTo(2);
		assertThat(accountRepository.findByUserId(admin.getId())).isPresent();
	}

	@Test
	void reseedingLeavesExistingTreasuryBalanceUntouched() {
		treasurySeeder.seed();
		Account treasury = accountRepository.findBySystemCode(Account.TREASURY).orElseThrow();
		treasury.applyChange(Money.of(-50000, "INR"));
		accountRepository.saveAndFlush(treasury);

		treasurySeeder.seed();

		assertThat(accountRepository.findBySystemCode(Account.TREASURY).orElseThrow().getBalance())
				.isEqualTo(Money.of(-50000, "INR"));
		assertThat(accountRepository.count()).isEqualTo(1);
	}

	@Test
	void reseedingNeverOverwritesAnExistingAdminsPassword() {
		adminSeeder.seed();
		String originalHash = userRepository.findByEmail("admin@zerosum.test").orElseThrow().getPasswordHash();

		adminSeeder.seed();

		assertThat(userRepository.findByEmail("admin@zerosum.test").orElseThrow().getPasswordHash())
				.isEqualTo(originalHash);
	}

	@Test
	void seededAdminCanLogInAndHasAdminRole() throws Exception {
		adminSeeder.seed();

		String body = mockMvc.perform(post("/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"email": "admin@zerosum.test", "password": "admin-test-Passw0rd"}
								"""))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		String token = jsonMapper.readTree(body).get("accessToken").asString();

		mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("admin@zerosum.test"))
				.andExpect(jsonPath("$.role").value("ADMIN"));
	}

}
