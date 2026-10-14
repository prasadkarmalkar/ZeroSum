package com.zerosum.zerosum.auth.web;

import java.time.Instant;

/** The newly registered user. Deliberately has no password hash. */
public record RegisterResponse(Long id, String email, Role role, Instant createdAt) {

	public static RegisterResponse from(User user) {
		return new RegisterResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
	}

}
