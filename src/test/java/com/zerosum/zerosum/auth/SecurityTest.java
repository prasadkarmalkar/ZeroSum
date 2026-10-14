package com.zerosum.zerosum.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.zerosum.zerosum.auth.repository.UserRepository;
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
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end checks of the security filter chain: tokens, 401/403 bodies, /auth/me and the Swagger scheme. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AuthService authService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private JwtEncoder jwtEncoder;

	@Autowired
	private SecurityProblemHandler problemHandler;

	@Autowired
	private UserRepository userRepository;

	private User user;

	@BeforeEach
	void registerUser() {
		userRepository.deleteAll();
		user = authService.register(new RegisterRequest("me@example.com", "s3cret-Passw0rd"));
	}

	// --- /auth/me ---

	@Test
	void meReturnsTheSignedInUser() throws Exception {
		String token = tokenService.issueAccessToken(user).value();

		getMe("Bearer " + token)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId()))
				.andExpect(jsonPath("$.email").value("me@example.com"))
				.andExpect(jsonPath("$.role").value("USER"));
	}

	// --- 401 ---

	@Test
	void missingTokenReturns401ProblemDetail() throws Exception {
		getMe(null)
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.title").value("Authentication required"))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.instance").value("/auth/me"));
	}

	@Test
	void malformedTokenReturns401() throws Exception {
		assertRejectedAsInvalidToken(getMe("Bearer not-a-jwt"));
	}

	@Test
	void expiredTokenReturns401() throws Exception {
		Instant past = Instant.now().minus(Duration.ofHours(2));
		String token = sign(jwtEncoder, claims("zerosum", past, past.plus(Duration.ofMinutes(15))));

		assertRejectedAsInvalidToken(getMe("Bearer " + token));
	}

	@Test
	void tokenFromAnotherIssuerReturns401() throws Exception {
		String token = sign(jwtEncoder, claims("someone-else", Instant.now(), Instant.now().plusSeconds(900)));

		assertRejectedAsInvalidToken(getMe("Bearer " + token));
	}

	@Test
	void tokenSignedWithAnotherKeyReturns401() throws Exception {
		RSAKey otherKey = new RSAKeyGenerator(2048).keyID("zerosum-key-1").generate();
		JwtEncoder forger = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(otherKey)));
		String token = sign(forger, claims("zerosum", Instant.now(), Instant.now().plusSeconds(900)));

		assertRejectedAsInvalidToken(getMe("Bearer " + token));
	}

	// --- 403 ---

	@Test
	void accessDeniedHandlerWrites403ProblemDetail() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/admin/anything");
		MockHttpServletResponse response = new MockHttpServletResponse();

		problemHandler.handle(request, response, new AccessDeniedException("denied"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		assertThat(response.getContentAsString())
				.contains("\"errorCode\":\"ACCESS_DENIED\"")
				.contains("\"title\":\"Access denied\"")
				.contains("\"instance\":\"/admin/anything\"");
	}

	// --- Swagger ---

	@Test
	void openApiDeclaresBearerSchemeAndKeepsRegisterPublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
				.andExpect(jsonPath("$.security[*].bearerAuth").exists())
				.andExpect(jsonPath("$.paths['/auth/register'].post.security").isEmpty())
				.andExpect(jsonPath("$.paths['/auth/me'].get.security").doesNotExist());
	}

	// --- helpers ---

	private ResultActions getMe(String authorization) throws Exception {
		var request = get("/auth/me");
		if (authorization != null) {
			request.header(HttpHeaders.AUTHORIZATION, authorization);
		}
		return mockMvc.perform(request);
	}

	private void assertRejectedAsInvalidToken(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("invalid_token")))
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
	}

	private JwtClaimsSet claims(String issuer, Instant issuedAt, Instant expiresAt) {
		return JwtClaimsSet.builder()
				.issuer(issuer)
				.subject(user.getId().toString())
				.issuedAt(issuedAt)
				.expiresAt(expiresAt)
				.claim("email", user.getEmail())
				.claim("roles", List.of("USER"))
				.build();
	}

	private static String sign(JwtEncoder encoder, JwtClaimsSet claims) {
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

}
