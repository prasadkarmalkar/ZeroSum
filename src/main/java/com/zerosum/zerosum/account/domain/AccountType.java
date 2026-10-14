package com.zerosum.zerosum.account.domain;

public enum AccountType {

	/** Owned by one user; its balance can never go negative. */
	USER,

	/** Owned by the system (e.g. TREASURY, the source of deposits); its balance may go negative. */
	SYSTEM

}
