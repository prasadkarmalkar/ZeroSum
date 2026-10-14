package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.common.ZerosumProperties;
import com.zerosum.zerosum.ledger.repository.AccountEntriesTotal;
import com.zerosum.zerosum.ledger.repository.EntryRepository;
import com.zerosum.zerosum.ledger.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Proves the ledger is whole: every entry ever written sums to zero, and every account's cached balance
 * equals the sum of its entries (ADR 0003).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrialBalanceService {

	private final EntryRepository entryRepository;
	private final LedgerTransactionRepository transactionRepository;
	private final AccountService accountService;
	private final ZerosumProperties properties;
	private final Clock clock = Clock.systemUTC();

	/**
	 * @param entriesTotal       sum of every entry ever
	 * @param balancesTotal      sum of every cached balance
	 * @param mismatchedAccounts accounts whose cached balance differs from the sum of their entries
	 *                           (including entries on an account id that doesn't exist)
	 */
	public record TrialBalance(boolean balanced, Money entriesTotal, Money balancesTotal,
			int mismatchedAccounts, long transactionCount, Instant checkedAt) {
	}

	/**
	 * Every query runs in one read-only {@link Isolation#REPEATABLE_READ} transaction, which in PostgreSQL
	 * means one snapshot for all of them. Without that, a transfer committing between "read the balances"
	 * and "read the entries" would show up as a mismatch that never really existed.
	 */
	@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
	public TrialBalance check() {
		long entriesTotal = entryRepository.sumOfAllEntries();
		long balancesTotal = accountService.totalCachedBalance();
		long transactionCount = transactionRepository.count();
		Map<Long, Long> cached = accountService.cachedBalances();
		Map<Long, Long> fromEntries = entryRepository.sumOfEntriesPerAccount().stream()
				.collect(Collectors.toMap(AccountEntriesTotal::accountId, AccountEntriesTotal::totalMinor));

		List<Long> mismatched = mismatchedAccounts(cached, fromEntries);
		boolean balanced = entriesTotal == 0 && mismatched.isEmpty();

		TrialBalance result = new TrialBalance(balanced, money(entriesTotal), money(balancesTotal),
				mismatched.size(), transactionCount, Instant.now(clock));
		if (balanced) {
			log.info("Trial balance OK: {} transactions, {} accounts", transactionCount, cached.size());
		} else {
			log.error("Trial balance FAILED: entries total {}, balances total {}, mismatched accounts {}",
					result.entriesTotal(), result.balancesTotal(), mismatched);
		}
		return result;
	}

	/** Every account id seen on either side, whose cached balance and entry total differ (a missing side counts as 0). */
	private static List<Long> mismatchedAccounts(Map<Long, Long> cached, Map<Long, Long> fromEntries) {
		Set<Long> ids = new HashSet<>(cached.keySet());
		ids.addAll(fromEntries.keySet());
		return ids.stream()
				.filter(id -> cached.getOrDefault(id, 0L).longValue() != fromEntries.getOrDefault(id, 0L).longValue())
				.sorted()
				.toList();
	}

	private Money money(long minor) {
		return new Money(minor, properties.currency());
	}

}
