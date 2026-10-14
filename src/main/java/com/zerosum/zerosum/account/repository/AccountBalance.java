package com.zerosum.zerosum.account.repository;

/** An account's cached balance, in minor units. */
public record AccountBalance(Long accountId, long balanceMinor) {
}
