package com.zerosum.zerosum.ledger.repository;

import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.domain.TransactionType;

import java.time.Instant;

/**
 * One row of an account statement: an entry, its transaction, and the account's balance right after it.
 *
 * @param amount       signed: negative left the account, positive came in
 * @param balanceAfter the running balance including this entry
 */
public record StatementLine(
		Long entryId,
		Long transactionId,
		TransactionType type,
		String description,
		Instant createdAt,
		Money amount,
		Money balanceAfter) {
}
