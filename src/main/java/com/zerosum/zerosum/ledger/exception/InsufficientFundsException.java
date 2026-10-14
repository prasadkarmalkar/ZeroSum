package com.zerosum.zerosum.ledger.exception;

import com.zerosum.zerosum.common.ApiException;
import com.zerosum.zerosum.common.Money;
import org.springframework.http.HttpStatus;

public class InsufficientFundsException extends ApiException {

	public InsufficientFundsException(Long accountId, Money available, Money requested) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_FUNDS", "Insufficient funds",
				"Account " + accountId + " has " + available + " available but " + requested + " was requested");
	}

}
