package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.ledger.domain.Entry;
import com.zerosum.zerosum.ledger.domain.LedgerTransaction;
import com.zerosum.zerosum.ledger.exception.TransactionNotFoundException;
import com.zerosum.zerosum.ledger.repository.LedgerTransactionRepository;
import com.zerosum.zerosum.ledger.web.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionQueryService {

	private final AccountService accountService;
	private final LedgerTransactionRepository transactionRepository;

	/**
	 * One transaction the user took part in, with their own side of it.
	 * <p>
	 * The client picks the id, so this must prove the caller is entitled to it: the caller's account comes
	 * from the token, and the query only finds the transaction if one of its entries is on that account.
	 * Someone else's transaction is reported exactly like an id that doesn't exist.
	 *
	 * @throws TransactionNotFoundException if the id doesn't exist or the user isn't part of it
	 * @throws AccountNotFoundException     if the user has no account
	 */
	@Transactional(readOnly = true)
	public TransactionResponse getForUser(Long userId, Long transactionId) {
		Long accountId = accountService.getForUser(userId).getId();
		LedgerTransaction transaction = transactionRepository.findByIdForAccount(transactionId, accountId)
				.orElseThrow(() -> new TransactionNotFoundException(transactionId));

		// An account appears at most once per transaction (a posting rule), so this is the caller's only entry
		Entry own = transaction.getEntries().stream()
				.filter(entry -> entry.getAccountId().equals(accountId))
				.findFirst()
				.orElseThrow();

		return new TransactionResponse(
				transaction.getId(),
				transaction.getType(),
				transaction.getDescription(),
				transaction.getCreatedAt(),
				own.getAmount().toDecimalString());
	}

}
