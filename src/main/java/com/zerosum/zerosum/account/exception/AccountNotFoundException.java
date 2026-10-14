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

	/** No registered user has this email, or that user has no account. */
	public static AccountNotFoundException forEmail(String email) {
		return new AccountNotFoundException("No account found for '" + email + "'");
	}

	private AccountNotFoundException(String detail) {
		super(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found", detail);
	}

}
