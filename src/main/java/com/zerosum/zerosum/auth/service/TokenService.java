package com.zerosum.zerosum.auth.service;

import com.zerosum.zerosum.auth.web.AccessToken;
import com.zerosum.zerosum.auth.web.JWTProperty;
import com.zerosum.zerosum.auth.web.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Issues signed JWT access tokens. */
@Service
@RequiredArgsConstructor
public class TokenService {

	private final JwtEncoder jwtEncoder;
	private final JWTProperty jwtProperty;

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

	/** How long each access token stays valid. */
	public Duration accessTokenTtl() {
		return jwtProperty.accessTokenTtl();
	}

}
