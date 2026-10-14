package com.zerosum.zerosum.ledger.exception;

import com.zerosum.zerosum.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The transaction doesn't exist, <em>or</em> the caller isn't part of it. The two cases are deliberately
 * indistinguishable (same status, code, title and detail), so a caller can't learn which transaction ids
 * exist by probing other people's.
 */
public class TransactionNotFoundException extends ApiException {

	public TransactionNotFoundException(Long transactionId) {
		super(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction not found",
				"Transaction " + transactionId + " was not found");
	}

}
