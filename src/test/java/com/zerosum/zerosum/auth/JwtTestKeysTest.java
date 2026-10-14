package com.zerosum.zerosum.auth;

import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.service.AuthService;
import com.zerosum.zerosum.auth.web.AccessToken;
import com.zerosum.zerosum.auth.web.RegisterRequest;
import com.zerosum.zerosum.auth.web.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.security.interfaces.RSAPublicKey;

import static org.assertj.core.api.Assertions.assertThat;

/** The test profile signs and verifies with the committed test-only key pair, not the local certs. */
@SpringBootTest
@ActiveProfiles("test")
class JwtTestKeysTest {

	@Autowired
	private RSAPublicKey jwtPublicKey;

	@Autowired
	private AuthService authService;

	@Autowired
	private JwtDecoder jwtDecoder;

	@Autowired
	private UserRepository userRepository;

	@Test
	void usesTheTestOnlyPublicKey() throws Exception {
		try (InputStream in = new ClassPathResource("test-keys/jwt-test-public.pem").getInputStream()) {
			assertThat(jwtPublicKey).isEqualTo(RsaKeyConverters.x509().convert(in));
		}
	}

	@Test
	void issuedTokenVerifiesWithTheTestKeys() {
		userRepository.deleteAll();
		User user = authService.register(new RegisterRequest("jwt@example.com", "s3cret-Passw0rd"));

		AccessToken token = authService.issueAccessToken(user);
		Jwt jwt = jwtDecoder.decode(token.value());

		assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
		assertThat(jwt.getClaimAsString("email")).isEqualTo("jwt@example.com");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
	}

}
