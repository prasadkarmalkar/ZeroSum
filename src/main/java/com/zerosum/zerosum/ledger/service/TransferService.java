package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.common.ZerosumProperties;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.exception.IdempotencyKeyReusedException;
import com.zerosum.zerosum.ledger.exception.InsufficientFundsException;
import com.zerosum.zerosum.ledger.exception.SelfTransferNotAllowedException;
import com.zerosum.zerosum.ledger.repository.LedgerTransactionRepository;
import com.zerosum.zerosum.ledger.web.TransferRequest;
import com.zerosum.zerosum.ledger.web.TransferResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TransferService {

	private static final String DEFAULT_DESCRIPTION = "Transfer";

	private final AccountService accountService;
	private final LedgerService ledgerService;
	private final LedgerTransactionRepository transactionRepository;
	private final IdempotencyService idempotencyService;
	private final ZerosumProperties properties;

	/**
	 * Moves money from the sender's account to the recipient's, at most once per idempotency key
	 * (see {@link IdempotencyService}).
	 *
	 * @param senderUserId the signed-in user, from the access token; never from the request
	 * @throws AccountNotFoundException        if the sender or recipient has no account
	 * @throws SelfTransferNotAllowedException if the recipient is the sender
	 * @throws InsufficientFundsException      if the sender's balance is too low
	 * @throws IdempotencyKeyReusedException   if the key was already used for a different request
	 */
	public IdempotentResult<TransferResponse> transfer(Long senderUserId, String idempotencyKey, TransferRequest request) {
		String email = MoneyRequests.normalizeEmail(request.recipientEmail());
		Money amount = Money.parse(request.amount(), properties.currency());
		String description = MoneyRequests.descriptionOrDefault(request.description(), DEFAULT_DESCRIPTION);

		return idempotencyService.execute(senderUserId, idempotencyKey, "TRANSFER",
				MoneyRequests.canonical(email, amount, description), TransferResponse.class,
				() -> doTransfer(senderUserId, email, amount, description),
				TransferResponse::transactionId);
	}

	private TransferResponse doTransfer(Long senderUserId, String email, Money amount, String description) {
		Account sender = accountService.getForUser(senderUserId);
		Account recipient = accountService.getForEmail(email);
		if (sender.getId().equals(recipient.getId())) {
			throw new SelfTransferNotAllowedException(sender.getId());
		}

		Long transactionId = ledgerService.post(TransactionType.TRANSFER, description, List.of(
				new EntryLine(sender.getId(), amount.negate()),
				new EntryLine(recipient.getId(), amount)));

		// Already in this transaction's persistence context, so no extra query
		var createdAt = transactionRepository.findById(transactionId).orElseThrow().getCreatedAt();
		return new TransferResponse(transactionId, amount.toDecimalString(), email, createdAt);
	}

}
