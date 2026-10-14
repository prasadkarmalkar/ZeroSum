package com.zerosum.zerosum.auth.web;

import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.service.AuthService;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LoginTest {

	private static final String PASSWORD = "s3cret-Passw0rd";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JsonMapper jsonMapper;

	@BeforeEach
	void registerUser() {
		userRepository.deleteAll();
		authService.register(new RegisterRequest("login@example.com", PASSWORD));
	}

	@Test
	void validCredentialsReturnBearerTokenThatWorksOnMe() throws Exception {
		String body = login("login@example.com", PASSWORD)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").value(startsWith("eyJ")))
				.andExpect(jsonPath("$.tokenType").value("Bearer"))
				.andExpect(jsonPath("$.expiresIn").value(3600))
				.andReturn().getResponse().getContentAsString();

		String token = jsonMapper.readTree(body).get("accessToken").asString();
		mockMvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("login@example.com"))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	@Test
	void emailIsMatchedIgnoringCase() throws Exception {
		login("LOGIN@Example.COM", PASSWORD).andExpect(status().isOk());
	}

	@Test
	void wrongPasswordAndUnknownEmailGetTheSameResponse() throws Exception {
		JsonNode wrongPassword = invalidCredentials(login("login@example.com", "wrong-password"));
		JsonNode unknownEmail = invalidCredentials(login("nobody@example.com", PASSWORD));

		assertThat(wrongPassword).isEqualTo(unknownEmail);
	}

	@Test
	void invalidRequestReturns400() throws Exception {
		login("not-an-email", "")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors.length()").value(2));
	}

	private JsonNode invalidCredentials(ResultActions result) throws Exception {
		String body = result
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
				.andExpect(jsonPath("$.detail").value("The email or password is incorrect"))
				.andReturn().getResponse().getContentAsString();
		return jsonMapper.readTree(body);
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email": "%s", "password": "%s"}
						""".formatted(email, password)));
	}

}
