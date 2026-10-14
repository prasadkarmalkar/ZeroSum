package com.zerosum.zerosum.account.service;

import com.zerosum.zerosum.account.domain.Account;
import com.zerosum.zerosum.account.domain.AccountType;
import com.zerosum.zerosum.common.Money;

import java.util.Currency;

/**
 * A read-only snapshot of an account, for other modules (like ledger) that must not hold or change
 * the {@link Account} entity itself.
 */
public record AccountView(Long id, AccountType type, Currency currency, Money balance) {

	static AccountView of(Account account) {
		return new AccountView(account.getId(), account.getType(), account.getCurrency(), account.getBalance());
	}

}
