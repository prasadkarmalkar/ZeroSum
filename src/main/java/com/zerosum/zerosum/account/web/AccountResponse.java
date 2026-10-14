package com.zerosum.zerosum.account.web;

import com.zerosum.zerosum.account.domain.Account;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What a user sees of their own account. Deliberately omits internal fields
 * (owner id, account type, system code, timestamps).
 *
 * @param balance a decimal string at the currency's scale (ADR 0001), e.g. "1050.00"
 */
public record AccountResponse(

		@Schema(example = "2")
		Long id,

		@Schema(example = "INR", description = "ISO 4217 currency code")
		String currency,

		@Schema(example = "1050.00", description = "Decimal string at the currency's scale, never a JSON number")
		String balance

) {

	public static AccountResponse from(Account account) {
		return new AccountResponse(
				account.getId(),
				account.getCurrency().getCurrencyCode(),
				account.getBalance().toDecimalString());
	}

}
