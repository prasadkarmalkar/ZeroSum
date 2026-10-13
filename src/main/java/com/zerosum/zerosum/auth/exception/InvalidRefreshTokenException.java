package com.zerosum.zerosum.auth.exception;

import com.zerosum.zerosum.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Deliberately takes no inputs: the token is a secret and must never be echoed back or logged.
 */
public class InvalidRefreshTokenException extends ApiException {

	public InvalidRefreshTokenException() {
		super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Invalid refresh token",
				"The refresh token is invalid, expired or revoked; please sign in again");
	}

}
