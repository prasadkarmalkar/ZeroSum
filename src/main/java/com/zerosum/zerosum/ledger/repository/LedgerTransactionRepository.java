package com.zerosum.zerosum.ledger.repository;

import com.zerosum.zerosum.ledger.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Append-only: extends the bare {@link Repository} instead of {@code JpaRepository}, so it exposes
 * only saving and reading, and there is no delete method to call by mistake.
 */
public interface LedgerTransactionRepository extends Repository<LedgerTransaction, Long> {

	LedgerTransaction save(LedgerTransaction transaction);

	LedgerTransaction saveAndFlush(LedgerTransaction transaction);

	Optional<LedgerTransaction> findById(Long id);

	/**
	 * The transaction, only if one of its entries is on the given account; otherwise empty, exactly as if the
	 * id didn't exist. The ownership check is part of the query itself, so there is no way to load someone
	 * else's transaction first and forget to check it.
	 */
	@Query("""
			select t from LedgerTransaction t
			where t.id = :id
			  and exists (select 1 from Entry e where e.transaction = t and e.accountId = :accountId)
			""")
	Optional<LedgerTransaction> findByIdForAccount(Long id, Long accountId);

	long count();

}
