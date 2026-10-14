package com.zerosum.zerosum.ledger.repository;

import com.zerosum.zerosum.ledger.domain.LedgerTransaction;
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

	long count();

}
