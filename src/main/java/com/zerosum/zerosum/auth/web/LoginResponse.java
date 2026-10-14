package com.zerosum.zerosum.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * @param accessToken the signed JWT to send as {@code Authorization: Bearer <accessToken>}
 * @param tokenType   always {@code Bearer}
 * @param expiresIn   seconds until the token expires
 */
public record LoginResponse(
		@Schema(example = "eyJraWQiOiJ6ZXJvc3VtLWtleS0xIiwiYWxnIjoiUlMyNTYifQ.eyJzdWIiOiIyIn0.c2lnbmF0dXJl",
				description = "Paste this into Swagger's Authorize dialog")
		String accessToken,
		@Schema(example = "Bearer") String tokenType,
		@Schema(example = "3600", description = "Seconds until the token expires") long expiresIn) {

	public static LoginResponse bearer(String accessToken, long expiresIn) {
		return new LoginResponse(accessToken, "Bearer", expiresIn);
	}

}
