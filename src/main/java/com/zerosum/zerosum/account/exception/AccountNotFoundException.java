package com.zerosum.zerosum.account.exception;

import com.zerosum.zerosum.common.ApiException;
import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends ApiException {

	public AccountNotFoundException(Long accountId) {
		this("Account " + accountId + " does not exist");
	}

	/** No account belongs to this user. */
	public static AccountNotFoundException forUser(Long userId) {
		return new AccountNotFoundException("User " + userId + " has no account");
	}

	private AccountNotFoundException(String detail) {
		super(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found", detail);
	}

}
