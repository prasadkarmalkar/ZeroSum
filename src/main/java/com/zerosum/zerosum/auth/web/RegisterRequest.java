package com.zerosum.zerosum.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

		@NotBlank
		@Email
		@Size(max = 255) // matches the users.email column
		String email,

		// Bcrypt only uses the first 72 bytes, so longer passwords would be silently truncated
		@NotBlank
		@Size(min = 8, max = 72)
		String password

) {

	/** Never print the password, e.g. when the request is logged. */
	@Override
	public String toString() {
		return "RegisterRequest[email=" + email + ", password=****]";
	}

}
