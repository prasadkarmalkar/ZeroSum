package com.zerosum.zerosum.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/** The signed-in user, as described by their access token. */
public record MeResponse(
		@Schema(example = "2") Long id,
		@Schema(example = "alice@example.com") String email,
		@Schema(example = "USER") Role role) {

	public static MeResponse from(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList("roles");
		return new MeResponse(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("email"), Role.valueOf(roles.getFirst()));
	}

}
