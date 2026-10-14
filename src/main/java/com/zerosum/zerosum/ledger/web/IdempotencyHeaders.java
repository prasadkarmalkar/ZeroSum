package com.zerosum.zerosum.ledger.web;

import com.zerosum.zerosum.ledger.service.IdempotentResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** The idempotency headers shared by every money-moving endpoint. */
final class IdempotencyHeaders {

	/** Request header: unique per operation, chosen by the client. */
	static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	/** Response header: "true" when the response is a replay of an earlier request with the same key. */
	static final String REPLAYED = "Idempotent-Replayed";

	static final String KEY_DESCRIPTION = "Unique per operation, chosen by the client (a UUID is ideal), at most 255 characters";

	static final String KEY_EXAMPLE = "3f1c2e9a-8b7d-4c1e-9f0a-6d2b5e8c4a71";

	private IdempotencyHeaders() {
	}

	/** 201 Created with the body, marked as a replay or not. */
	static <T> ResponseEntity<T> created(IdempotentResult<T> result) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.header(REPLAYED, Boolean.toString(result.replayed()))
				.body(result.response());
	}

}
