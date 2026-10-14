package com.zerosum.zerosum.auth.service;

import com.zerosum.zerosum.auth.exception.EmailAlreadyRegisteredException;
import com.zerosum.zerosum.auth.exception.InvalidCredentialsException;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.web.*;
import com.zerosum.zerosum.common.event.UserRegisteredEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventPublisher;
	private final TokenService tokenService;

	/** Hash of a random password, checked when the email is unknown; see {@link #login}. */
	private String dummyPasswordHash;

	@PostConstruct
	void initDummyPasswordHash() {
		dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	/**
	 * Registers a new user with the {@link Role#USER} role and publishes a {@link UserRegisteredEvent}.
	 * <p>
	 * Everything runs in one transaction, so if a listener running inside it fails, the user is not saved.
	 * Listeners that should only run once the user is committed should use
	 * {@code @TransactionalEventListener} (which defaults to after commit).
	 *
	 * @throws EmailAlreadyRegisteredException if the email (ignoring case) is already registered
	 */
	@Transactional
	public User register(RegisterRequest request) {
		String email = User.normalizeEmail(request.email());
		if (userRepository.existsByEmail(email)) {
			throw new EmailAlreadyRegisteredException(email);
		}

		User user;
		try {
			// Flush now so a concurrent registration that slipped past the check above fails here, as a 409
			user = userRepository.saveAndFlush(new User(email, passwordEncoder.encode(request.password()), Role.USER));
		} catch (DataIntegrityViolationException e) {
			throw new EmailAlreadyRegisteredException(email);
		}

		eventPublisher.publishEvent(new UserRegisteredEvent(user.getId()));
		log.info("Registered user {}", user.getId());
		return user;
	}

	/**
	 * Checks the credentials and issues an access token.
	 *
	 * @throws InvalidCredentialsException if the email is unknown or the password is wrong; the two cases
	 *                                     are indistinguishable, so the response can't reveal which emails exist
	 */
	@Transactional(readOnly = true)
	public LoginResponse login(LoginRequest request) {
		String email = User.normalizeEmail(request.email());
		Optional<User> user = userRepository.findByEmail(email);

		// Check a password even when the user doesn't exist, so both failures take the same time
		String hash = user.map(User::getPasswordHash).orElse(dummyPasswordHash);
		boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
		if (user.isEmpty() || !passwordMatches) {
			throw new InvalidCredentialsException();
		}

		AccessToken token = tokenService.issueAccessToken(user.get());
		log.info("User {} logged in", user.get().getId());
		return LoginResponse.bearer(token.value(), tokenService.accessTokenTtl().toSeconds());
	}

}
