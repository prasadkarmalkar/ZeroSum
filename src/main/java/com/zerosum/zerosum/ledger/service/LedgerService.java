package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.account.domain.AccountType;
import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.account.service.AccountView;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.LedgerTransaction;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.exception.InsufficientFundsException;
import com.zerosum.zerosum.ledger.exception.LedgerInvariantViolationException;
import com.zerosum.zerosum.ledger.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

	private final LedgerTransactionRepository transactionRepository;
	private final AccountService accountService;

	/**
	 * Posts a transaction, all in one database transaction (joining the caller's, if any):
	 * <ol>
	 *   <li>checks the posting rules</li>
	 *   <li>locks every account involved, lowest id first, until commit</li>
	 *   <li>checks each account exists and matches its entry's currency</li>
	 *   <li>checks no USER account would go below zero, using the locked balances (SYSTEM accounts are exempt)</li>
	 *   <li>saves the transaction and its entries</li>
	 *   <li>applies each entry to its account's cached balance (see ADR 0003)</li>
	 * </ol>
	 * If anything fails, nothing is saved and no balance changes.
	 *
	 * @return the new transaction's id
	 * @throws LedgerInvariantViolationException if a posting rule is broken or an entry's currency doesn't
	 *                                           match its account; logged at error level here
	 * @throws AccountNotFoundException          if an entry's account doesn't exist
	 * @throws InsufficientFundsException        if a USER account's balance would go below zero
	 */
	@Transactional
	public Long post(TransactionType type, String description, List<EntryLine> lines) {
		LedgerTransaction transaction;
		try {
			transaction = LedgerTransaction.create(type, description, lines);
		} catch (LedgerInvariantViolationException e) {
			throw logRejection(type, description, lines, e);
		}

		Map<Long, AccountView> accounts = lockAccounts(lines);
		for (EntryLine line : lines) {
			Currency accountCurrency = accounts.get(line.accountId()).currency();
			if (!accountCurrency.equals(line.amount().currency())) {
				throw logRejection(type, description, lines, new LedgerInvariantViolationException("Entry for account "
						+ line.accountId() + " is in " + line.amount().currency() + " but the account is in " + accountCurrency));
			}
		}

		requireNoOverdraft(lines, accounts);

		Long id = transactionRepository.save(transaction).getId();
		accountService.applyBalanceChanges(lines.stream()
				.collect(Collectors.toMap(EntryLine::accountId, EntryLine::amount)));
		log.info("Posted {} transaction {} with {} entries", type, id, lines.size());
		return id;
	}

	/**
	 * Checked against the balances read under lock, so no concurrent posting can change them
	 * between this check and the balance update.
	 */
	private static void requireNoOverdraft(List<EntryLine> lines, Map<Long, AccountView> accounts) {
		for (EntryLine line : lines) {
			AccountView account = accounts.get(line.accountId());
			if (account.type() == AccountType.USER && account.balance().add(line.amount()).isNegative()) {
				throw new InsufficientFundsException(account.id(), account.balance(), line.amount().negate());
			}
		}
	}

	/** Logged here, whoever the caller is; a bug in the caller, so it's an error, not a warning. */
	private LedgerInvariantViolationException logRejection(TransactionType type, String description,
			List<EntryLine> lines, LedgerInvariantViolationException e) {
		log.error("Rejected {} posting \"{}\" with lines {}: {}", type, description, lines, e.getMessage());
		return e;
	}

	private Map<Long, AccountView> lockAccounts(List<EntryLine> lines) {
		List<Long> ids = lines.stream().map(EntryLine::accountId).toList();
		return accountService.lockAccounts(ids).stream()
				.collect(Collectors.toMap(AccountView::id, Function.identity()));
	}

}
