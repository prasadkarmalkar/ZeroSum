package com.zerosum.zerosum.ledger.service;

import com.zerosum.zerosum.common.Money;

import java.util.Locale;

/** Normalization shared by the "send money to an email" operations (transfers and deposits). */
final class MoneyRequests {

	private MoneyRequests() {
	}

	/** Same normalization as stored emails (ledger can't use auth's helper under the module rules). */
	static String normalizeEmail(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

	static String descriptionOrDefault(String description, String defaultDescription) {
		return description == null || description.isBlank() ? defaultDescription : description.strip();
	}

	/**
	 * The request in the form used for its idempotency fingerprint, built from already-normalized values, so
	 * requests that mean the same thing match: "BOB@x.com" and "bob@x.com", "100" and "100.00", and a missing
	 * description and the default.
	 */
	static String canonical(String normalizedEmail, Money amount, String description) {
		return String.join("\n", normalizedEmail, Long.toString(amount.amountMinor()), description);
	}

}
