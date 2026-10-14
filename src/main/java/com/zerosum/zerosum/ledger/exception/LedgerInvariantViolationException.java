package com.zerosum.zerosum.ledger.exception;

import com.zerosum.zerosum.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * A ledger posting broke one of the posting rules (see {@code LedgerTransaction#create}).
 * <p>
 * Services build postings from already-validated requests, so reaching this means a bug in our code,
 * not a client error: it is a 500, the detail is logged at error level, and the client only gets a
 * generic message plus the {@code LEDGER_INVARIANT_VIOLATED} code.
 */
public class LedgerInvariantViolationException extends ApiException {

	public LedgerInvariantViolationException(String detail) {
		super(HttpStatus.INTERNAL_SERVER_ERROR, "LEDGER_INVARIANT_VIOLATED", "Ledger invariant violated", detail);
	}

}
