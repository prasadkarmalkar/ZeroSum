package com.zerosum.zerosum.ledger.exception;

import com.zerosum.zerosum.common.Money;

/**
 * The most important posting rule: a transaction's entries must sum to exactly zero.
 * Same status and code as every other rule violation.
 */
public class UnbalancedTransactionException extends LedgerInvariantViolationException {

	public UnbalancedTransactionException(Money entriesSum) {
		super("Transaction entries sum to " + entriesSum + " instead of zero");
	}

}
