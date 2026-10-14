package com.zerosum.zerosum.ledger.web;

import com.zerosum.zerosum.ledger.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One transaction, from the caller's point of view: only their own side of it. The other party's account,
 * and any other entry, are deliberately left out.
 *
 * @param amount the caller's signed amount, as a decimal string (ADR 0001): "-200.00" left their account
 */
public record TransactionResponse(

		@Schema(example = "17")
		Long id,

		@Schema(example = "TRANSFER")
		TransactionType type,

		@Schema(example = "Dinner")
		String description,

		Instant createdAt,

		@Schema(example = "-200.00", description = "The caller's own signed amount: negative left their account")
		String amount

) {
}
