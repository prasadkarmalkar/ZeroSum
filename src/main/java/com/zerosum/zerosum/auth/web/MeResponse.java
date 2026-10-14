package com.zerosum.zerosum.auth.web;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/** The signed-in user, as described by their access token. */
public record MeResponse(Long id, String email, Role role) {

	public static MeResponse from(Jwt jwt) {
		List<String> roles = jwt.getClaimAsStringList("roles");
		return new MeResponse(Long.valueOf(jwt.getSubject()), jwt.getClaimAsString("email"), Role.valueOf(roles.getFirst()));
	}

}
