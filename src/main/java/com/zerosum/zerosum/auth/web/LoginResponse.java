package com.zerosum.zerosum.auth.web;

/**
 * @param accessToken the signed JWT to send as {@code Authorization: Bearer <accessToken>}
 * @param tokenType   always {@code Bearer}
 * @param expiresIn   seconds until the token expires
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn) {

	public static LoginResponse bearer(String accessToken, long expiresIn) {
		return new LoginResponse(accessToken, "Bearer", expiresIn);
	}

}
