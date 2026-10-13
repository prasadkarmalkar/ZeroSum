package com.zerosum.zerosum.ledger.exception;

import com.zerosum.zerosum.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class SelfTransferNotAllowedException extends ApiException {

	public SelfTransferNotAllowedException(UUID accountId) {
		super(HttpStatus.UNPROCESSABLE_CONTENT, "SELF_TRANSFER_NOT_ALLOWED", "Self-transfer not allowed",
				"Cannot transfer from account " + accountId + " to itself");
	}

}
