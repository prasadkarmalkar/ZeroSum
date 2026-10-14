package com.zerosum.zerosum.auth.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(

		@Schema(example = "alice@example.com", description = "Stored lowercase; must not already be registered")
		@NotBlank
		@Email
		@Size(max = 255) // matches the users.email column
		String email,

		// Bcrypt only uses the first 72 bytes, so longer passwords would be silently truncated
		@Schema(example = "s3cret-Passw0rd", description = "8 to 72 characters")
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
