package com.zerosum.zerosum.common.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Documentation only: the exact JSON every error response has (RFC 9457 problem details plus our
 * {@code errorCode}). The handlers build it as a {@code ProblemDetail}, whose generated schema would show
 * a vague {@code properties} map instead of these fields.
 */
@Schema(name = "ErrorResponse", description = "An error, as RFC 9457 problem details (application/problem+json)")
public record ErrorResponse(

		@Schema(example = "about:blank")
		String type,

		@Schema(example = "Insufficient funds", description = "Short, human-readable summary; may change")
		String title,

		@Schema(example = "422")
		int status,

		@Schema(example = "Account 3 has 100.00 INR available but 150.00 INR was requested",
				description = "Human-readable detail for this occurrence; may change")
		String detail,

		@Schema(example = "/transfers", description = "The request path")
		String instance,

		@Schema(example = "INSUFFICIENT_FUNDS", description = "Stable, machine-readable code; branch on this")
		String errorCode,

		@Schema(description = "Only for VALIDATION_FAILED: each invalid field and why", nullable = true)
		List<FieldError> errors

) {

	public record FieldError(
			@Schema(example = "amount") String field,
			@Schema(example = "must be at least 0.01") String message) {
	}

}
