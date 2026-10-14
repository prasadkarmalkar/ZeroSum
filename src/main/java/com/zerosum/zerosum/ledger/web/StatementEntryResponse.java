package com.zerosum.zerosum.ledger.web;

import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.repository.StatementLine;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One statement row. Amounts are decimal strings (ADR 0001).
 *
 * @param amount       signed: "-200.00" left the account, "300.00" came in
 * @param balanceAfter the account's balance right after this entry
 */
public record StatementEntryResponse(

		@Schema(example = "17")
		Long transactionId,

		@Schema(example = "TRANSFER")
		TransactionType type,

		@Schema(example = "Dinner")
		String description,

		@Schema(example = "-200.00", description = "Signed decimal string: negative left the account")
		String amount,

		@Schema(example = "800.00", description = "Decimal string: the balance right after this entry")
		String balanceAfter,

		Instant createdAt

) {

	static StatementEntryResponse from(StatementLine line) {
		return new StatementEntryResponse(
				line.transactionId(),
				line.type(),
				line.description(),
				line.amount().toDecimalString(),
				line.balanceAfter().toDecimalString(),
				line.createdAt());
	}

}
