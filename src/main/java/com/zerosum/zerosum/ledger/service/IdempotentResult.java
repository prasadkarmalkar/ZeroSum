package com.zerosum.zerosum.ledger.service;

/**
 * An operation's response, and whether it was replayed from an earlier request with the same
 * Idempotency-Key (sent to the client as the {@code Idempotent-Replayed} header).
 */
public record IdempotentResult<T>(T response, boolean replayed) {
}
