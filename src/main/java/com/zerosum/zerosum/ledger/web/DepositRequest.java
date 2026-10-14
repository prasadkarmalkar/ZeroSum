package com.zerosum.zerosum.ledger.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * An admin paying money into a user's account, out of TREASURY.
 *
 * @param amount      a positive decimal string (ADR 0001), e.g. "500.00"
 * @param description optional; defaults to "Deposit"
 */
public record DepositRequest(

		@Schema(example = "alice@example.com")
		@NotBlank
		@Email
		@Size(max = 255)
		String recipientEmail,

		@Schema(example = "500.00", description = "Positive decimal string with at most 2 decimal places")
		@NotBlank
		@Pattern(regexp = "\\d{1,15}(\\.\\d{1,2})?", message = "must be a decimal string like 12.50, with at most 2 decimal places")
		@DecimalMin(value = "0.01", message = "must be at least 0.01")
		String amount,

		@Schema(example = "Welcome bonus", nullable = true)
		@Size(max = 255)
		String description

) {
}
