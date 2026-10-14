package com.zerosum.zerosum.auth.web;

import com.zerosum.zerosum.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack: security filter chain, controller, service and the real zerosum_test database. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void cleanDatabase() {
		userRepository.deleteAll();
	}

	@Test
	void registersUserWithoutAuthentication() throws Exception {
		register("Alice@Example.com", "s3cret-Passw0rd")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id", notNullValue()))
				.andExpect(jsonPath("$.email").value("alice@example.com"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt", notNullValue()))
				.andExpect(content().string(not(containsString("s3cret-Passw0rd"))))
				.andExpect(content().string(not(containsString("password"))));

		User saved = userRepository.findByEmail("alice@example.com").orElseThrow();
		assertThat(saved.getPasswordHash()).isNotEqualTo("s3cret-Passw0rd");
		assertThat(passwordEncoder.matches("s3cret-Passw0rd", saved.getPasswordHash())).isTrue();
	}

	@Test
	void invalidRequestReturns400WithEachField() throws Exception {
		register("not-an-email", "short")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors.length()").value(2));

		assertThat(userRepository.count()).isZero();
	}

	@Test
	void duplicateEmailIgnoringCaseReturns409() throws Exception {
		register("bob@example.com", "s3cret-Passw0rd").andExpect(status().isCreated());

		register("BOB@example.com", "another-Passw0rd")
				.andExpect(status().isConflict())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("EMAIL_ALREADY_REGISTERED"))
				.andExpect(jsonPath("$.detail").value("An account with email 'bob@example.com' already exists"));

		assertThat(userRepository.count()).isEqualTo(1);
	}

	@Test
	void restOfAuthPathsStillRequireAuthentication() throws Exception {
		mockMvc.perform(get("/auth/register")).andExpect(status().isForbidden());
		mockMvc.perform(post("/auth/something-else")).andExpect(status().isForbidden());
	}

	private ResultActions register(String email, String password) throws Exception {
		return mockMvc.perform(post("/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}
						""".formatted(email, password)));
	}

}
