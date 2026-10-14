package com.zerosum.zerosum.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Only checks the shape of the input. The password has no length rules here, so the response
 * never hints at the password policy; a wrong password is simply invalid credentials.
 */
public record LoginRequest(

		@Schema(example = "alice@example.com", description = "Matched ignoring case")
		@NotBlank
		@Email
		@Size(max = 255)
		String email,

		@Schema(example = "s3cret-Passw0rd")
		@NotBlank
		String password

) {

	/** Never print the password, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "LoginRequest[email=" + email + ", password=****]";
	}

}
