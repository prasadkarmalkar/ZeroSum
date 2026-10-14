package com.zerosum.zerosum.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Only checks the shape of the input. The password has no length rules here, so the response
 * never hints at the password policy; a wrong password is simply invalid credentials.
 */
public record LoginRequest(

		@NotBlank
		@Email
		@Size(max = 255)
		String email,

		@NotBlank
		String password

) {

	/** Never print the password, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=****]";
	}

}
