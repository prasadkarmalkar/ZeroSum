package com.zerosum.zerosum.account.service;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.repository.AccountRepository;
import com.zerosum.zerosum.auth.service.UserLookup;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.common.ZerosumProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

	private final AccountRepository accountRepository;
	private final ZerosumProperties properties;
	private final UserLookup userLookup;

	/**
	 * Opens a user's account in the project currency, with a zero balance.
	 * <p>
	 * Joins the caller's transaction if there is one (as during registration), so the account is
	 * saved or rolled back together with the user.
	 */
	@Transactional
	public Account openUserAccount(Long userId) {
		Account account = accountRepository.saveAndFlush(Account.forUser(userId, properties.currency()));
		log.info("Opened account {} for user {}", account.getId(), userId);
		return account;
	}

	/** Opens a system account, e.g. {@link Account#TREASURY}, in the project currency with a zero balance. */
	@Transactional
	public Account openSystemAccount(String systemCode) {
		Account account = accountRepository.saveAndFlush(Account.system(systemCode, properties.currency()));
		log.info("Opened system account {} ({})", account.getId(), systemCode);
		return account;
	}

	/** @throws AccountNotFoundException if the user has no account */
	@Transactional(readOnly = true)
	public Account getForUser(Long userId) {
		return accountRepository.findByUserId(userId)
				.orElseThrow(() -> AccountNotFoundException.forUser(userId));
	}

	/**
	 * Finds the account of the user with this email (matched ignoring case), resolving the user through
	 * the auth module's public {@link UserLookup}.
	 *
	 * @throws AccountNotFoundException if no user has this email, or that user has no account
	 */
	@Transactional(readOnly = true)
	public Account getForEmail(String email) {
		return userLookup.findIdByEmail(email)
				.flatMap(accountRepository::findByUserId)
				.orElseThrow(() -> AccountNotFoundException.forEmail(email));
	}

	/**
	 * Locks the given accounts' rows for the rest of the current transaction and returns a view of each,
	 * in ascending id order.
	 * <p>
	 * Rows are always locked lowest id first, whatever order the ids are given in. Two transactions that
	 * touch the same accounts (e.g. A to B and B to A) therefore queue on the same first row instead of
	 * each holding one row and waiting forever for the other. Duplicate ids are locked once.
	 * <p>
	 * {@link Propagation#MANDATORY}: a lock taken outside a transaction would be released immediately,
	 * so calling this without one is a bug and fails fast with an {@code IllegalTransactionStateException}.
	 *
	 * @throws AccountNotFoundException if any id has no account
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public List<AccountView> lockAccounts(Collection<Long> accountIds) {
		List<AccountView> locked = new ArrayList<>();
		for (Long id : new TreeSet<>(accountIds)) {
			Account account = accountRepository.findByIdForUpdate(id)
					.orElseThrow(() -> new AccountNotFoundException(id));
			locked.add(AccountView.of(account));
		}
		return List.copyOf(locked);
	}

	/**
	 * Adds each signed change to its account's cached balance, in ascending id order.
	 * <p>
	 * Re-takes each row lock (a no-op if the caller already holds it, as {@code LedgerService.post} does
	 * via {@link #lockAccounts}), so balances are never written without a lock. A USER account going
	 * negative here means the caller skipped its overdraft check: {@link Account#applyChange} throws, and
	 * the database CHECK backs that up.
	 * <p>
	 * {@link Propagation#MANDATORY}: the changes must commit or roll back together with the ledger
	 * entries they mirror, so calling this without a transaction is a bug and fails fast.
	 *
	 * @throws AccountNotFoundException if any id has no account
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void applyBalanceChanges(Map<Long, Money> changes) {
		new TreeMap<>(changes).forEach((id, change) -> {
			Account account = accountRepository.findByIdForUpdate(id)
					.orElseThrow(() -> new AccountNotFoundException(id));
			account.applyChange(change);
		});
	}

}
