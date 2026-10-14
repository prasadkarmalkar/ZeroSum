package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.ledger.domain.IdempotencyKey;
import com.zerosum.zerosum.ledger.exception.IdempotencyKeyReusedException;
import com.zerosum.zerosum.ledger.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runs a money-moving operation at most once per (user, Idempotency-Key). Used by transfers and deposits.
 * <ul>
 *   <li>New key: runs the work, and stores the key with the response in the same database transaction.</li>
 *   <li>Known key, same request: doesn't run anything; replays the stored response.</li>
 *   <li>Known key, different request (or a different operation): {@link IdempotencyKeyReusedException} (409).</li>
 * </ul>
 * If the work fails, everything rolls back, the key included, so the client can retry the same key.
 * <p>
 * Two requests racing with the same new key: the key row is inserted <em>before</em> the work runs, so the
 * second insert waits on the unique index until the first transaction ends. When the first commits, the
 * second insert fails, rolling back the second request before it moves any money; it then replays (or
 * rejects) from the winner's row in a fresh transaction. That's why this class manages its own
 * transactions instead of being {@code @Transactional}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

	private final IdempotencyKeyRepository repository;
	private final TransactionTemplate transactionTemplate;
	private final JsonMapper jsonMapper;

	/**
	 * @param userId           the caller, from the access token; keys are scoped per user
	 * @param key              the client's Idempotency-Key
	 * @param operation        a stable name like "TRANSFER", so one key can't be reused across operations
	 * @param canonicalRequest the request in a normalized form, so equivalent requests match
	 * @param responseType     the response class, for replaying the stored JSON
	 * @param work             the operation itself; runs inside this method's transaction
	 * @param transactionIdOf  extracts the ledger transaction id from the response, stored for reference
	 */
	public <T> IdempotentResult<T> execute(Long userId, String key, String operation, String canonicalRequest,
			Class<T> responseType, Supplier<T> work, Function<T, Long> transactionIdOf) {
		String fingerprint = sha256(operation + "\n" + canonicalRequest);
		try {
			return transactionTemplate.execute(status -> {
				var existing = repository.findByUserIdAndKey(userId, key);
				if (existing.isPresent()) {
					return replayOrReject(existing.get(), key, fingerprint, responseType);
				}

				// Claim the key first: a concurrent request with the same key now waits on this row until we finish
				IdempotencyKey claimed = repository.saveAndFlush(new IdempotencyKey(userId, key, fingerprint));
				T response = work.get();
				claimed.complete(transactionIdOf.apply(response), jsonMapper.writeValueAsString(response));
				return new IdempotentResult<>(response, false);
			});
		} catch (DataIntegrityViolationException e) {
			if (!isKeyConflict(e)) {
				throw e;
			}
			log.info("Idempotency key race for user {} ({}); replaying the winner's result", userId, operation);
			return transactionTemplate.execute(status -> replayOrReject(
					repository.findByUserIdAndKey(userId, key).orElseThrow(), key, fingerprint, responseType));
		}
	}

	private <T> IdempotentResult<T> replayOrReject(IdempotencyKey stored, String key, String fingerprint, Class<T> type) {
		if (!stored.matches(fingerprint)) {
			throw new IdempotencyKeyReusedException(key);
		}
		return new IdempotentResult<>(jsonMapper.readValue(stored.getResponseBody(), type), true);
	}

	private static String sha256(String text) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available", e);
		}
	}

	private static boolean isKeyConflict(DataIntegrityViolationException e) {
		for (Throwable cause = e; cause != null; cause = cause.getCause()) {
			if (cause instanceof ConstraintViolationException cve
					&& IdempotencyKey.UNIQUE_CONSTRAINT.equalsIgnoreCase(cve.getConstraintName())) {
				return true;
			}
		}
		return false;
	}

}
