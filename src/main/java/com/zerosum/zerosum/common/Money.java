package com.zerosum.zerosum.common;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in a single currency, stored as minor units (e.g. cents).
 * Amounts may be negative: in double-entry, a debit is a negative amount.
 * Immutable; every operation returns a new instance.
 */
public record Money(long amountMinor, Currency currency) {

	public Money {
		Objects.requireNonNull(currency, "currency must not be null");
	}

	/** Creates Money from minor units and an ISO 4217 code, e.g. of(1050, "INR") = 10.50 INR. */
	public static Money of(long amountMinor, String currencyCode) {
		Objects.requireNonNull(currencyCode, "currencyCode must not be null");
		// Throws IllegalArgumentException for unknown codes
		return new Money(amountMinor, Currency.getInstance(currencyCode));
	}

	/**
	 * Parses a plain decimal amount, the API format from ADR 0001, e.g. {@code parse("12.50", INR)} = 1250 minor units.
	 * The inverse of {@link #toDecimalString()}.
	 *
	 * @throws IllegalArgumentException if the text isn't a decimal number, has more decimal places than the
	 *                                  currency allows, or doesn't fit in a {@code long}
	 */
	public static Money parse(String decimal, Currency currency) {
		Objects.requireNonNull(decimal, "decimal must not be null");
		Objects.requireNonNull(currency, "currency must not be null");
		int scale = Math.max(currency.getDefaultFractionDigits(), 0);
		try {
			return new Money(new BigDecimal(decimal).movePointRight(scale).longValueExact(), currency);
		} catch (NumberFormatException | ArithmeticException e) {
			throw new IllegalArgumentException(
					"'" + decimal + "' is not a valid " + currency.getCurrencyCode() + " amount with at most "
							+ scale + " decimal places", e);
		}
	}

	public static Money zero(Currency currency) {
		return new Money(0L, currency);
	}

	public Money add(Money other) {
		requireSameCurrency(other);
		return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
	}

	public Money subtract(Money other) {
		requireSameCurrency(other);
		return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
	}

	public Money negate() {
		return new Money(Math.negateExact(amountMinor), currency);
	}

	public boolean isZero() {
		return amountMinor == 0L;
	}

	public boolean isNegative() {
		return amountMinor < 0L;
	}

	public boolean isPositive() {
		return amountMinor > 0L;
	}

	/**
	 * The amount as a plain decimal at the currency's scale, without the currency, e.g. {@code 1050.00}.
	 * This is the API format for amounts (a JSON string, so clients never parse it as a float).
	 */
	public String toDecimalString() {
		int scale = Math.max(currency.getDefaultFractionDigits(), 0);
		return BigDecimal.valueOf(amountMinor, scale).toPlainString();
	}

	/** Human-readable form, e.g. {@code 10.50 INR}. */
	@Override
	public String toString() {
		return toDecimalString() + " " + currency.getCurrencyCode();
	}

	private void requireSameCurrency(Money other) {
		Objects.requireNonNull(other, "other must not be null");
		if (!currency.equals(other.currency)) {
			throw new IllegalArgumentException(
					"Currency mismatch: " + currency + " vs " + other.currency);
		}
	}
}