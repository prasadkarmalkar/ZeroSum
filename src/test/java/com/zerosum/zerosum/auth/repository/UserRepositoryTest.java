package com.zerosum.zerosum.auth.repository;

import com.zerosum.zerosum.auth.domain.Role;
import com.zerosum.zerosum.auth.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Runs against the real zerosum_test PostgreSQL database, so the unique constraint is enforced by Postgres. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@Test
	void savesAndFindsByEmail() {
		User saved = userRepository.saveAndFlush(new User("Alice@Example.com", "{bcrypt}hash", Role.USER));

		assertThat(saved.getId()).isNotNull();
		assertThat(saved.getCreatedAt()).isNotNull();

		User found = userRepository.findByEmail("alice@example.com").orElseThrow();
		assertThat(found.getId()).isEqualTo(saved.getId());
		assertThat(found.getEmail()).isEqualTo("alice@example.com");
		assertThat(found.getPasswordHash()).isEqualTo("{bcrypt}hash");
		assertThat(found.getRole()).isEqualTo(Role.USER);
	}

	@Test
	void checksWhetherEmailExists() {
		userRepository.saveAndFlush(new User("bob@example.com", "{bcrypt}hash", Role.ADMIN));

		assertThat(userRepository.existsByEmail("bob@example.com")).isTrue();
		assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
		assertThat(userRepository.findByEmail("nobody@example.com")).isEmpty();
	}

	@Test
	void databaseRejectsDuplicateEmail() {
		userRepository.saveAndFlush(new User("carol@example.com", "{bcrypt}hash1", Role.USER));

		// Differs only in case: normalized to the same email, so the database must reject it
		assertThatThrownBy(() -> userRepository.saveAndFlush(new User("CAROL@example.com", "{bcrypt}hash2", Role.USER)))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("uk_users_email");
	}

}
