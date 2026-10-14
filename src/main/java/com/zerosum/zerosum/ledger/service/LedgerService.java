package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.LedgerTransaction;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.exception.LedgerInvariantViolationException;
import com.zerosum.zerosum.ledger.repository.LedgerTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LedgerService {

	private final LedgerTransactionRepository transactionRepository;

	/**
	 * Validates the lines against the posting rules, then saves the transaction and its entries
	 * in one transaction (joining the caller's, if any).
	 *
	 * @return the new transaction's id
	 * @throws LedgerInvariantViolationException if a posting rule is broken; logged at error level
	 *                                           here, and nothing is saved
	 */
	@Transactional
	public Long post(TransactionType type, String description, List<EntryLine> lines) {
		LedgerTransaction transaction;
		try {
			transaction = LedgerTransaction.create(type, description, lines);
		} catch (LedgerInvariantViolationException e) {
			// Logged here, whoever the caller is; a bug in the caller, so it's an error, not a warning
			log.error("Rejected {} posting \"{}\" with lines {}: {}", type, description, lines, e.getMessage());
			throw e;
		}

		Long id = transactionRepository.save(transaction).getId();
		log.info("Posted {} transaction {} with {} entries", type, id, lines.size());
		return id;
	}

}
