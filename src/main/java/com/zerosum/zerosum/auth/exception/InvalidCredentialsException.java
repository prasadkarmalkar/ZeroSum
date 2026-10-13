package com.zerosum.zerosum.auth.exception;

import com.zerosum.zerosum.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * Deliberately takes no inputs: the message must not reveal whether the email exists
 * or only the password was wrong.
 */
public class InvalidCredentialsException extends ApiException {

	public InvalidCredentialsException() {
		super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid credentials",
				"The email or password is incorrect");
	}

}
