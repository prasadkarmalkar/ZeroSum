package com.zerosum.zerosum.ledger.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * @param amount         the amount deposited, as a decimal string (ADR 0001)
 * @param recipientEmail the recipient's email, normalized to lowercase
 */
public record DepositResponse(

		@Schema(example = "12")
		Long transactionId,

		@Schema(example = "500.00")
		String amount,

		@Schema(example = "alice@example.com")
		String recipientEmail,

		Instant createdAt

) {
}
