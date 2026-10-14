package com.zerosum.zerosum.auth.service;

import com.nimbusds.jose.JWSHeader;
import com.zerosum.zerosum.auth.exception.EmailAlreadyRegisteredException;
import com.zerosum.zerosum.auth.repository.UserRepository;
import com.zerosum.zerosum.auth.web.*;
import com.zerosum.zerosum.common.event.UserRegisteredEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final ApplicationEventPublisher eventPublisher;
	private final JwtEncoder jwtEncoder;
	private final JWTProperty jwtProperty;

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

	public AccessToken issueAccessToken(User user) {
		Instant now = Instant.now();
		Instant expiresAt = now.plus(jwtProperty.accessTokenTtl());
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(jwtProperty.issuer())
				.subject(user.getId().toString())
				.issuedAt(now)
				.expiresAt(expiresAt)
				.id(UUID.randomUUID().toString())
				.claim("email", user.getEmail())
				.claim("roles", List.of(user.getRole().name()))
				.build();

		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();

		String token = jwtEncoder
				.encode(JwtEncoderParameters.from(header, claims))
				.getTokenValue();
		return new AccessToken(token, expiresAt);
	}

}
