package com.zerosum.zerosum.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/** The newly registered user. Deliberately has no password hash. */
public record RegisterResponse(
		@Schema(example = "2") Long id,
		@Schema(example = "alice@example.com") String email,
		@Schema(example = "USER") Role role,
		Instant createdAt) {

	public static RegisterResponse from(User user) {
		return new RegisterResponse(user.getId(), user.getEmail(), user.getRole(), user.getCreatedAt());
	}

}
