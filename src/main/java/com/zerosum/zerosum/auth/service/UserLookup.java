package com.zerosum.zerosum.auth.service;

import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.web.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * The auth module's public, read-only API for other modules: resolves users without exposing
 * the {@code User} entity or {@code UserRepository}.
 */
@Service
@RequiredArgsConstructor
public class UserLookup {

	private final UserRepository userRepository;

	/** The id of the user with this email, matched ignoring case and surrounding spaces. */
	@Transactional(readOnly = true)
	public Optional<Long> findIdByEmail(String email) {
		return userRepository.findByEmail(User.normalizeEmail(email)).map(User::getId);
	}

}
