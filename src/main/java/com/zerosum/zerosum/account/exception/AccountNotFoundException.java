package com.zerosum.zerosum.account.exception;

import com.zerosum.zerosum.common.ApiException;
import org.springframework.http.HttpStatus;

import java.util.UUID;

public class AccountNotFoundException extends ApiException {

	public AccountNotFoundException(UUID accountId) {
		super(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found",
				"Account " + accountId + " does not exist");
	}

}
