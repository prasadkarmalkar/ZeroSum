package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.common.ZerosumProperties;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.LedgerTransaction;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.exception.InsufficientFundsException;
import com.zerosum.zerosum.ledger.exception.SelfTransferNotAllowedException;
import com.zerosum.zerosum.ledger.repository.LedgerTransactionRepository;
import com.zerosum.zerosum.ledger.web.TransferRequest;
import com.zerosum.zerosum.ledger.web.TransferResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class TransferService {

	private static final String DEFAULT_DESCRIPTION = "Transfer";

	private final AccountService accountService;
	private final LedgerService ledgerService;
	private final LedgerTransactionRepository transactionRepository;
	private final ZerosumProperties properties;

	/**
	 * Moves money from the sender's account to the recipient's, in one transaction.
	 *
	 * @param senderUserId the signed-in user, from the access token; never from the request
	 * @throws AccountNotFoundException         if the sender or recipient has no account
	 * @throws SelfTransferNotAllowedException  if the recipient is the sender
	 * @throws InsufficientFundsException       if the sender's balance is too low
	 */
	@Transactional
	public TransferResponse transfer(Long senderUserId, TransferRequest request) {
		Account sender = accountService.getForUser(senderUserId);
		Account recipient = accountService.getForEmail(request.recipientEmail());
		if (sender.getId().equals(recipient.getId())) {
			throw new SelfTransferNotAllowedException(sender.getId());
		}

		Money amount = Money.parse(request.amount(), properties.currency());
		String description = request.description() == null || request.description().isBlank()
				? DEFAULT_DESCRIPTION
				: request.description().strip();

		Long transactionId = ledgerService.post(TransactionType.TRANSFER, description, List.of(
				new EntryLine(sender.getId(), amount.negate()),
				new EntryLine(recipient.getId(), amount)));

		// Already in this transaction's persistence context, so no extra query
		LedgerTransaction transaction = transactionRepository.findById(transactionId).orElseThrow();
		return new TransferResponse(transactionId, amount.toDecimalString(), normalizedEmail(request), transaction.getCreatedAt());
	}

	/** Same normalization as stored emails (ledger can't use auth's helper under the module rules). */
	private static String normalizedEmail(TransferRequest request) {
		return request.recipientEmail().strip().toLowerCase(Locale.ROOT);
	}

}
