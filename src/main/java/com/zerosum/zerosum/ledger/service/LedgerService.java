package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.account.service.AccountView;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.LedgerTransaction;
import com.zerosum.zerosum.ledger.domain.TransactionType;
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
	 * Validates the lines against the posting rules, locks every account involved (lowest id first,
	 * held until commit), checks each account exists and matches its entry's currency, then saves the
	 * transaction and its entries, all in one transaction (joining the caller's, if any).
	 *
	 * @return the new transaction's id
	 * @throws LedgerInvariantViolationException if a posting rule is broken or an entry's currency doesn't
	 *                                           match its account; logged at error level here, nothing is saved
	 * @throws AccountNotFoundException          if an entry's account doesn't exist; nothing is saved
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

		Long id = transactionRepository.save(transaction).getId();
		log.info("Posted {} transaction {} with {} entries", type, id, lines.size());
		return id;
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
