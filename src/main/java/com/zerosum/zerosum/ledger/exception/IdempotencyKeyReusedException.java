package com.zerosum.zerosum.ledger.exception;

import com.zerosum.zerosum.common.ApiException;
import org.springframework.http.HttpStatus;

/**
 * The same idempotency key was sent again with a different request body.
 * (A retry with the same body is not an error; it replays the original result.)
 */
public class IdempotencyKeyReusedException extends ApiException {

	public IdempotencyKeyReusedException(String idempotencyKey) {
		super(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "Idempotency key reused",
				"Idempotency key '" + idempotencyKey + "' was already used for a different request");
	}

}
