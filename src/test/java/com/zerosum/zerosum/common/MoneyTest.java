package com.zerosum.zerosum.common;

import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {

	private static final Currency INR = Currency.getInstance("INR");

	@Test
	void createsFromMinorUnitsAndCurrencyCode() {
		Money money = Money.of(1050, "INR");

		assertEquals(1050, money.amountMinor());
		assertEquals(INR, money.currency());
		assertEquals(new Money(1050, INR), money);
	}

	@Test
	void rejectsNullAndUnknownCurrency() {
		assertThrows(NullPointerException.class, () -> new Money(1, null));
		assertThrows(NullPointerException.class, () -> Money.of(1, null));
		assertThrows(IllegalArgumentException.class, () -> Money.of(1, "XYZ"));
	}

	@Test
	void addsAndSubtracts() {
		Money a = Money.of(1050, "INR");
		Money b = Money.of(325, "INR");

		assertEquals(Money.of(1375, "INR"), a.add(b));
		assertEquals(Money.of(725, "INR"), a.subtract(b));
		assertEquals(Money.of(-725, "INR"), b.subtract(a));
	}

	@Test
	void rejectsCurrencyMismatch() {
		Money inr = Money.of(100, "INR");
		Money usd = Money.of(100, "USD");

		assertThrows(IllegalArgumentException.class, () -> inr.add(usd));
		assertThrows(IllegalArgumentException.class, () -> inr.subtract(usd));
		assertThrows(NullPointerException.class, () -> inr.add(null));
	}

	@Test
	void throwsOnOverflow() {
		Money max = Money.of(Long.MAX_VALUE, "INR");
		Money min = Money.of(Long.MIN_VALUE, "INR");
		Money one = Money.of(1, "INR");

		assertThrows(ArithmeticException.class, () -> max.add(one));
		assertThrows(ArithmeticException.class, () -> min.subtract(one));
		assertThrows(ArithmeticException.class, min::negate);
	}

	@Test
	void negatesAndChecksSign() {
		Money five = Money.of(500, "INR");
		Money zero = Money.zero(INR);

		assertEquals(Money.of(-500, "INR"), five.negate());
		assertTrue(five.isPositive());
		assertFalse(five.isNegative());
		assertFalse(five.isZero());

		assertTrue(five.negate().isNegative());
		assertFalse(five.negate().isPositive());

		assertTrue(zero.isZero());
		assertFalse(zero.isNegative());
		assertFalse(zero.isPositive());
		assertTrue(zero.negate().isZero());
	}

}
