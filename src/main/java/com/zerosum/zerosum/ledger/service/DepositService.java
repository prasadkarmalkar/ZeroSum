package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.common.ZerosumProperties;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.exception.IdempotencyKeyReusedException;
import com.zerosum.zerosum.ledger.repository.LedgerTransactionRepository;
import com.zerosum.zerosum.ledger.web.DepositRequest;
import com.zerosum.zerosum.ledger.web.DepositResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepositService {

	private static final String DEFAULT_DESCRIPTION = "Deposit";

	private final AccountService accountService;
	private final LedgerService ledgerService;
	private final LedgerTransactionRepository transactionRepository;
	private final IdempotencyService idempotencyService;
	private final ZerosumProperties properties;

	/**
	 * Pays money into a user's account out of TREASURY, which goes negative by exactly that amount:
	 * this is how money enters the zero-sum system. At most once per idempotency key.
	 * <p>
	 * Only reachable by admins: {@code /admin/**} requires the ADMIN role.
	 *
	 * @param adminUserId the signed-in admin, from the access token; scopes the idempotency key
	 * @throws AccountNotFoundException      if the recipient has no account
	 * @throws IdempotencyKeyReusedException if the key was already used for a different request
	 */
	public IdempotentResult<DepositResponse> deposit(Long adminUserId, String idempotencyKey, DepositRequest request) {
		String email = MoneyRequests.normalizeEmail(request.recipientEmail());
		Money amount = Money.parse(request.amount(), properties.currency());
		String description = MoneyRequests.descriptionOrDefault(request.description(), DEFAULT_DESCRIPTION);

		return idempotencyService.execute(adminUserId, idempotencyKey, "DEPOSIT",
				MoneyRequests.canonical(email, amount, description), DepositResponse.class,
				() -> doDeposit(adminUserId, email, amount, description),
				DepositResponse::transactionId);
	}

	private DepositResponse doDeposit(Long adminUserId, String email, Money amount, String description) {
		Long treasury = accountService.getSystemAccountId(Account.TREASURY);
		Long recipient = accountService.getForEmail(email).getId();

		// TREASURY is a SYSTEM account, so the overdraft check in post() lets it go negative
		Long transactionId = ledgerService.post(TransactionType.DEPOSIT, description, List.of(
				new EntryLine(treasury, amount.negate()),
				new EntryLine(recipient, amount)));
		log.info("Admin {} deposited {} into account {} (transaction {})", adminUserId, amount, recipient, transactionId);

		var createdAt = transactionRepository.findById(transactionId).orElseThrow().getCreatedAt();
		return new DepositResponse(transactionId, amount.toDecimalString(), email, createdAt);
	}

}
