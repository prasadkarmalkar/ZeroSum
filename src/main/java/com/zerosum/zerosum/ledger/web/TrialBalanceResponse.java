package com.zerosum.zerosum.ledger.web;

import com.zerosum.zerosum.ledger.service.TrialBalanceService.TrialBalance;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * The ledger's health check. Amounts are decimal strings (ADR 0001).
 *
 * @param balanced           true when entriesTotal is zero and no account is mismatched
 * @param entriesTotal       sum of every ledger entry ever written; "0.00" in a healthy ledger
 * @param balancesTotal      sum of every account's cached balance; "0.00" in a healthy ledger
 * @param mismatchedAccounts accounts whose cached balance differs from the sum of their entries
 */
public record TrialBalanceResponse(

		@Schema(example = "true", description = "entriesTotal is zero and no account is mismatched")
		boolean balanced,

		@Schema(example = "0.00", description = "Sum of every ledger entry ever written")
		String entriesTotal,

		@Schema(example = "0.00", description = "Sum of every account's cached balance")
		String balancesTotal,

		@Schema(example = "0", description = "Accounts whose cached balance differs from the sum of their entries")
		int mismatchedAccounts,

		@Schema(example = "42")
		long transactionCount,

		Instant checkedAt

) {

	static TrialBalanceResponse from(TrialBalance trialBalance) {
		return new TrialBalanceResponse(
				trialBalance.balanced(),
				trialBalance.entriesTotal().toDecimalString(),
				trialBalance.balancesTotal().toDecimalString(),
				trialBalance.mismatchedAccounts(),
				trialBalance.transactionCount(),
				trialBalance.checkedAt());
	}

}
