package com.zerosum.zerosum.ledger.repository;

import com.zerosum.zerosum.ledger.domain.Entry;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * Read-only totals over ledger entries, for the trial balance. Extends the bare {@link Repository},
 * so there is nothing here that could write or delete an entry.
 */
public interface EntryRepository extends Repository<Entry, Long> {

	/** The sum of every entry ever written, in minor units; always 0 in a healthy ledger. */
	@Query("select coalesce(sum(e.amountMinor), 0) from Entry e")
	long sumOfAllEntries();

	/** The sum of each account's entries, in minor units; accounts without entries are absent. */
	@Query("""
			select new com.zerosum.zerosum.ledger.repository.AccountEntriesTotal(e.accountId, sum(e.amountMinor))
			from Entry e
			group by e.accountId
			""")
	List<AccountEntriesTotal> sumOfEntriesPerAccount();

}
