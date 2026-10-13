package com.zerosum.zerosum.auth.repository;

import com.zerosum.zerosum.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Emails are stored lowercase, so pass them through {@link User#normalizeEmail(String)} first.
 */
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String normalizedEmail);

	boolean existsByEmail(String normalizedEmail);

}
