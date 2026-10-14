package com.zerosum.zerosum.ledger.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Deliberately has no sender field: the sender is always the signed-in user, taken from the access token.
 *
 * @param amount      a positive decimal string (ADR 0001), e.g. "12.50"
 * @param description optional; defaults to "Transfer"
 */
public record TransferRequest(

		@Schema(example = "bob@example.com")
		@NotBlank
		@Email
		@Size(max = 255)
		String recipientEmail,

		@Schema(example = "12.50", description = "Positive decimal string with at most 2 decimal places")
		@NotBlank
		@Pattern(regexp = "\\d{1,15}(\\.\\d{1,2})?", message = "must be a decimal string like 12.50, with at most 2 decimal places")
		@DecimalMin(value = "0.01", message = "must be at least 0.01")
		String amount,

		@Schema(example = "Dinner", nullable = true)
		@Size(max = 255)
		String description

) {
}
