package com.zerosum.zerosum.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * A client-supplied {@code Idempotency-Key} for one user, with a fingerprint of the request it was first
 * used for and the response to replay.
 * <p>
 * The row is inserted <em>before</em> the transfer runs and completed in the same transaction, so other
 * transactions only ever see completed rows: a concurrent request with the same key blocks on the unique
 * index until this one commits or rolls back.
 */
@Entity
@Table(
		name = "idempotency_keys",
		uniqueConstraints = @UniqueConstraint(name = IdempotencyKey.UNIQUE_CONSTRAINT, columnNames = {"user_id", "idempotency_key"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA only
public class IdempotencyKey {

	public static final String UNIQUE_CONSTRAINT = "uk_idempotency_keys_user_key";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Column(name = "idempotency_key", nullable = false, updatable = false)
	private String key;

	/** SHA-256 (hex) of the normalized request; see {@code TransferService}. */
	@Column(name = "request_fingerprint", nullable = false, updatable = false, length = 64)
	private String requestFingerprint;

	/** The transaction this key produced; set when the request completes. */
	@Column(name = "transaction_id")
	private Long transactionId;

	/** The original response, as JSON, replayed for repeats; set when the request completes. */
	@Column(name = "response_body", columnDefinition = "text")
	private String responseBody;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	public IdempotencyKey(Long userId, String key, String requestFingerprint) {
		this.userId = Objects.requireNonNull(userId, "userId must not be null");
		this.key = Objects.requireNonNull(key, "key must not be null");
		this.requestFingerprint = Objects.requireNonNull(requestFingerprint, "requestFingerprint must not be null");
	}

	public void complete(Long transactionId, String responseBody) {
		if (this.transactionId != null) {
			throw new IllegalStateException("Idempotency key " + id + " is already complete");
		}
		this.transactionId = Objects.requireNonNull(transactionId, "transactionId must not be null");
		this.responseBody = Objects.requireNonNull(responseBody, "responseBody must not be null");
	}

	public boolean matches(String fingerprint) {
		return requestFingerprint.equals(fingerprint);
	}

}
