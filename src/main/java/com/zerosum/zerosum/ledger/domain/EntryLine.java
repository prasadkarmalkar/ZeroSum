package com.zerosum.zerosum.ledger.domain;

import com.zerosum.zerosum.common.Money;

import java.util.Objects;

/**
 * One line of a transaction before it is saved: a signed amount on one account
 * (negative to debit, positive to credit). {@link LedgerTransaction#create} turns lines into entries.
 */
public record EntryLine(Long accountId, Money amount) {

	public EntryLine {
		Objects.requireNonNull(accountId, "accountId must not be null");
		Objects.requireNonNull(amount, "amount must not be null");
	}

}
