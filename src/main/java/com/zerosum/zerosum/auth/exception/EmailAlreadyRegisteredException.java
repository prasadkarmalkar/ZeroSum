package com.zerosum.zerosum.auth.exception;

import com.zerosum.zerosum.common.ApiException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends ApiException {

	public EmailAlreadyRegisteredException(String email) {
		super(HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "Email already registered",
				"An account with email '" + email + "' already exists");
	}

}
