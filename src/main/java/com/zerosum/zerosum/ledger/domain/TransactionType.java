package com.zerosum.zerosum.ledger.domain;

public enum TransactionType {

	/** Money paid into a user's account out of TREASURY; admin only. */
	DEPOSIT,

	/** Money moved from one user's account to another's. */
	TRANSFER

}
