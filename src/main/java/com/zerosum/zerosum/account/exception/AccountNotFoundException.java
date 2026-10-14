package com.zerosum.zerosum.account.exception;

import com.zerosum.zerosum.common.ApiException;
import org.springframework.http.HttpStatus;

public class AccountNotFoundException extends ApiException {

	public AccountNotFoundException(Long accountId) {
		super(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found",
				"Account " + accountId + " does not exist");
	}

}
