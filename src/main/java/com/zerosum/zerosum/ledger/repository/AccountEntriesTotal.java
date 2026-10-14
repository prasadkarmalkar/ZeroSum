package com.zerosum.zerosum.ledger.repository;

/** The sum of one account's ledger entries, in minor units. */
public record AccountEntriesTotal(Long accountId, long totalMinor) {
}
