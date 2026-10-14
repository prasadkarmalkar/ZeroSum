package com.zerosum.zerosum.ledger.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * @param amount         the amount sent, as a decimal string (ADR 0001)
 * @param recipientEmail the recipient's email, normalized to lowercase
 */
public record TransferResponse(

		@Schema(example = "17")
		Long transactionId,

		@Schema(example = "12.50")
		String amount,

		@Schema(example = "bob@example.com")
		String recipientEmail,

		Instant createdAt

) {
}
