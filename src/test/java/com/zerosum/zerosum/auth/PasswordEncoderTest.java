package com.zerosum.zerosum.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordEncoderTest {

	private final PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

	@Test
	void hashIsNotTheRawPassword() {
		String hash = encoder.encode("s3cret-Passw0rd");

		assertThat(hash).isNotEqualTo("s3cret-Passw0rd").doesNotContain("s3cret-Passw0rd");
		assertThat(hash).startsWith("$2a$10$");
	}

	@Test
	void matchesTheRightPassword() {
		String hash = encoder.encode("s3cret-Passw0rd");

		assertThat(encoder.matches("s3cret-Passw0rd", hash)).isTrue();
	}

	@Test
	void rejectsAWrongPassword() {
		String hash = encoder.encode("s3cret-Passw0rd");

		assertThat(encoder.matches("wrong-password", hash)).isFalse();
	}

}
