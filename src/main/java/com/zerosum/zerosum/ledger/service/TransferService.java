package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.common.ZerosumProperties;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.IdempotencyKey;
import com.zerosum.zerosum.ledger.domain.LedgerTransaction;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.exception.IdempotencyKeyReusedException;
import com.zerosum.zerosum.ledger.exception.InsufficientFundsException;
import com.zerosum.zerosum.ledger.exception.SelfTransferNotAllowedException;
import com.zerosum.zerosum.ledger.repository.IdempotencyKeyRepository;
import com.zerosum.zerosum.ledger.repository.LedgerTransactionRepository;
import com.zerosum.zerosum.ledger.web.TransferRequest;
import com.zerosum.zerosum.ledger.web.TransferResponse;
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
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

	private static final String DEFAULT_DESCRIPTION = "Transfer";

	private final AccountService accountService;
	private final LedgerService ledgerService;
	private final LedgerTransactionRepository transactionRepository;
	private final IdempotencyKeyRepository idempotencyKeyRepository;
	private final ZerosumProperties properties;
	private final TransactionTemplate transactionTemplate;
	private final JsonMapper jsonMapper;

	/** The response, and whether it was replayed from an earlier request with the same key. */
	public record TransferResult(TransferResponse response, boolean replayed) {
	}

	/**
	 * Moves money from the sender's account to the recipient's, at most once per idempotency key.
	 * <ul>
	 *   <li>New key: the transfer runs, and the key is stored with the response in the same transaction.</li>
	 *   <li>Known key, same request: nothing runs again; the stored response is replayed.</li>
	 *   <li>Known key, different request: rejected with {@link IdempotencyKeyReusedException} (409).</li>
	 * </ul>
	 * Failed requests (4xx) store nothing, so the same key can be retried once the problem is fixed.
	 * <p>
	 * Not {@code @Transactional} itself: if two requests with the same new key race, the loser's insert
	 * waits on the unique index, fails once the winner commits, and that rolls back the loser's whole
	 * transaction. The loser then replays (or rejects) in a fresh transaction.
	 *
	 * @param senderUserId the signed-in user, from the access token; never from the request
	 * @throws AccountNotFoundException        if the sender or recipient has no account
	 * @throws SelfTransferNotAllowedException if the recipient is the sender
	 * @throws InsufficientFundsException      if the sender's balance is too low
	 * @throws IdempotencyKeyReusedException   if the key was already used for a different request
	 */
	public TransferResult transfer(Long senderUserId, String idempotencyKey, TransferRequest request) {
		String fingerprint = fingerprint(request);
		try {
			return transactionTemplate.execute(status -> {
				var existing = idempotencyKeyRepository.findByUserIdAndKey(senderUserId, idempotencyKey);
				if (existing.isPresent()) {
					return replayOrReject(existing.get(), idempotencyKey, fingerprint);
				}
				return process(senderUserId, idempotencyKey, fingerprint, request);
			});
		} catch (DataIntegrityViolationException e) {
			if (!isKeyConflict(e)) {
				throw e;
			}
			log.info("Idempotency key race for user {}; replaying the winner's result", senderUserId);
			return transactionTemplate.execute(status -> replayOrReject(
					idempotencyKeyRepository.findByUserIdAndKey(senderUserId, idempotencyKey).orElseThrow(),
					idempotencyKey, fingerprint));
		}
	}

	private TransferResult process(Long senderUserId, String idempotencyKey, String fingerprint, TransferRequest request) {
		// Claim the key first: a concurrent request with the same key now waits on this row until we finish
		IdempotencyKey key = idempotencyKeyRepository.saveAndFlush(new IdempotencyKey(senderUserId, idempotencyKey, fingerprint));

		Account sender = accountService.getForUser(senderUserId);
		Account recipient = accountService.getForEmail(request.recipientEmail());
		if (sender.getId().equals(recipient.getId())) {
			throw new SelfTransferNotAllowedException(sender.getId());
		}

		Money amount = amountOf(request);
		Long transactionId = ledgerService.post(TransactionType.TRANSFER, descriptionOf(request), List.of(
				new EntryLine(sender.getId(), amount.negate()),
				new EntryLine(recipient.getId(), amount)));

		// Already in this transaction's persistence context, so no extra query
		LedgerTransaction transaction = transactionRepository.findById(transactionId).orElseThrow();
		TransferResponse response = new TransferResponse(
				transactionId, amount.toDecimalString(), normalizedEmail(request), transaction.getCreatedAt());

		key.complete(transactionId, jsonMapper.writeValueAsString(response));
		return new TransferResult(response, false);
	}

	private TransferResult replayOrReject(IdempotencyKey key, String idempotencyKey, String fingerprint) {
		if (!key.matches(fingerprint)) {
			throw new IdempotencyKeyReusedException(idempotencyKey);
		}
		return new TransferResult(jsonMapper.readValue(key.getResponseBody(), TransferResponse.class), true);
	}

	/**
	 * SHA-256 of the request after normalization, so requests that mean the same transfer match:
	 * the email ignoring case, "10" and "10.00", and a missing or blank description and "Transfer".
	 */
	private String fingerprint(TransferRequest request) {
		String canonical = String.join("\n",
				normalizedEmail(request),
				Long.toString(amountOf(request).amountMinor()),
				descriptionOf(request));
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is always available", e);
		}
	}

	private Money amountOf(TransferRequest request) {
		return Money.parse(request.amount(), properties.currency());
	}

	private static String descriptionOf(TransferRequest request) {
		return request.description() == null || request.description().isBlank()
				? DEFAULT_DESCRIPTION
				: request.description().strip();
	}

	/** Same normalization as stored emails (ledger can't use auth's helper under the module rules). */
	private static String normalizedEmail(TransferRequest request) {
		return request.recipientEmail().strip().toLowerCase(Locale.ROOT);
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
