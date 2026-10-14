package com.zerosum.zerosum.account.domain;

import com.zerosum.zerosum.common.Money;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

/**
 * An account that holds money: either a user's own account or a system account such as TREASURY.
 * <p>
 * Built only through {@link #forUser} and {@link #system}, so a half-configured account can't exist.
 * The balance is a cached total of the account's ledger entries and changes only via {@link #applyChange}.
 */
@Entity
@Table(
		name = "accounts",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_accounts_user_id", columnNames = "user_id"),
				@UniqueConstraint(name = "uk_accounts_system_code", columnNames = "system_code")
		},
		check = {
				@CheckConstraint(name = "ck_accounts_user_balance_non_negative",
						constraint = "type <> 'USER' OR balance_minor >= 0"),
				@CheckConstraint(name = "ck_accounts_owner_matches_type",
						constraint = "(type = 'USER' AND user_id IS NOT NULL AND system_code IS NULL)"
								+ " OR (type = 'SYSTEM' AND user_id IS NULL AND system_code IS NOT NULL)")
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA only
public class Account {

	/** Code of the system account that deposits are paid out of; its balance goes negative by design. */
	public static final String TREASURY = "TREASURY";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** The owning user's id; a plain column so account doesn't depend on auth. Null for system accounts. */
	@Column(name = "user_id")
	private Long userId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AccountType type;

	/** Unique name of a system account, e.g. {@code TREASURY}. Null for user accounts. */
	@Column(name = "system_code", length = 50)
	private String systemCode;

	@Column(nullable = false, length = 3)
	private Currency currency;

	@Getter(AccessLevel.NONE) // exposed as Money via getBalance()
	@Column(nullable = false)
	private long balanceMinor;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Account(AccountType type, Long userId, String systemCode, Currency currency) {
		this.type = type;
		this.userId = userId;
		this.systemCode = systemCode;
		this.currency = Objects.requireNonNull(currency, "currency must not be null");
		this.balanceMinor = 0;
	}

	/** A user's own account, starting at zero. */
	public static Account forUser(Long userId, Currency currency) {
		return new Account(AccountType.USER, Objects.requireNonNull(userId, "userId must not be null"), null, currency);
	}

	/** A system account such as {@code TREASURY}, starting at zero. */
	public static Account system(String systemCode, Currency currency) {
		Objects.requireNonNull(systemCode, "systemCode must not be null");
		if (systemCode.isBlank()) {
			throw new IllegalArgumentException("systemCode must not be blank");
		}
		return new Account(AccountType.SYSTEM, null, systemCode, currency);
	}

	public Money getBalance() {
		return new Money(balanceMinor, currency);
	}

	/**
	 * Adds a signed amount to the balance: positive to credit, negative to debit.
	 *
	 * @throws IllegalArgumentException if the change is in a different currency
	 * @throws IllegalStateException    if a USER account would go negative; callers (the ledger)
	 *                                  must check for sufficient funds first, so this is a bug
	 * @throws ArithmeticException      if the balance would overflow
	 */
	public void applyChange(Money change) {
		Money newBalance = getBalance().add(change);
		if (type == AccountType.USER && newBalance.isNegative()) {
			throw new IllegalStateException(
					"Account " + id + " would go negative: " + getBalance() + " + " + change + " = " + newBalance);
		}
		balanceMinor = newBalance.amountMinor();
	}

}
