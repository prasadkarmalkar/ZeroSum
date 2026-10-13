package com.zerosum.zerosum.ledger.exception;

import com.zerosum.zerosum.common.ApiException;
import com.zerosum.zerosum.common.Money;
import org.springframework.http.HttpStatus;

/**
 * A transaction's entries do not sum to zero. This is a bug in our code, not a client error,
 * so it is a 500: the detail below is logged, and the client only gets a generic message.
 */
public class UnbalancedTransactionException extends ApiException {

	public UnbalancedTransactionException(Money entriesSum) {
		super(HttpStatus.INTERNAL_SERVER_ERROR, "LEDGER_INVARIANT_VIOLATED", "Ledger invariant violated",
				"Transaction entries sum to " + entriesSum + " instead of zero");
	}

}
