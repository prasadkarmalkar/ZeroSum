package com.zerosum.zerosum.account.domain;

import com.zerosum.zerosum.common.Money;
import org.junit.jupiter.api.Test;

import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

	private static final Currency INR = Currency.getInstance("INR");

	@Test
	void userAccountFactorySetsUserFields() {
		Account account = Account.forUser(7L, INR);

		assertThat(account.getType()).isEqualTo(AccountType.USER);
		assertThat(account.getUserId()).isEqualTo(7L);
		assertThat(account.getSystemCode()).isNull();
		assertThat(account.getBalance()).isEqualTo(Money.zero(INR));
	}

	@Test
	void systemAccountFactorySetsSystemFields() {
		Account account = Account.system("TREASURY", INR);

		assertThat(account.getType()).isEqualTo(AccountType.SYSTEM);
		assertThat(account.getUserId()).isNull();
		assertThat(account.getSystemCode()).isEqualTo("TREASURY");
		assertThat(account.getBalance()).isEqualTo(Money.zero(INR));
	}

	@Test
	void factoriesRejectMissingFields() {
		assertThatThrownBy(() -> Account.forUser(null, INR)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Account.forUser(7L, null)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Account.system(null, INR)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> Account.system("  ", INR)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void applyChangeCreditsAndDebits() {
		Account account = Account.forUser(7L, INR);

		account.applyChange(Money.of(1000, "INR"));
		account.applyChange(Money.of(-250, "INR"));

		assertThat(account.getBalance()).isEqualTo(Money.of(750, "INR"));
	}

	@Test
	void userAccountCannotGoNegative() {
		Account account = Account.forUser(7L, INR);
		account.applyChange(Money.of(100, "INR"));

		assertThatThrownBy(() -> account.applyChange(Money.of(-101, "INR")))
				.isInstanceOf(IllegalStateException.class);
		assertThat(account.getBalance()).isEqualTo(Money.of(100, "INR"));
	}

	@Test
	void systemAccountMayGoNegative() {
		Account treasury = Account.system("TREASURY", INR);

		treasury.applyChange(Money.of(-5000, "INR"));

		assertThat(treasury.getBalance()).isEqualTo(Money.of(-5000, "INR"));
	}

	@Test
	void rejectsChangeInAnotherCurrency() {
		Account account = Account.forUser(7L, INR);

		assertThatThrownBy(() -> account.applyChange(Money.of(100, "USD")))
				.isInstanceOf(IllegalArgumentException.class);
	}

}
