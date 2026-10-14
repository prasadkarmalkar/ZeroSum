package com.zerosum.zerosum.ledger.domain;

import com.zerosum.zerosum.common.Money;
import jakarta.persistence.CheckConstraint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.util.Currency;

/**
 * One signed amount on one account, belonging to a {@link LedgerTransaction}: negative debits the
 * account, positive credits it. Never zero.
 * <p>
 * The account is referenced only by id (a plain column), so the ledger doesn't map account entities.
 * Created only by {@link LedgerTransaction#create}; immutable and never deleted.
 */
@Entity
@Immutable
@Table(
		name = "ledger_entries",
		indexes = @Index(name = "idx_ledger_entries_account_id", columnList = "account_id"),
		check = @CheckConstraint(name = "ck_ledger_entries_amount_non_zero", constraint = "amount_minor <> 0"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA only
public class Entry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "transaction_id", nullable = false, updatable = false)
	private LedgerTransaction transaction;

	@Column(name = "account_id", nullable = false, updatable = false)
	private Long accountId;

	@Getter(AccessLevel.NONE) // exposed as Money via getAmount()
	@Column(nullable = false, updatable = false)
	private long amountMinor;

	@Column(nullable = false, updatable = false, length = 3)
	private Currency currency;

	Entry(LedgerTransaction transaction, Long accountId, Money amount) {
		this.transaction = transaction;
		this.accountId = accountId;
		this.amountMinor = amount.amountMinor();
		this.currency = amount.currency();
	}

	public Money getAmount() {
		return new Money(amountMinor, currency);
	}

	@PreRemove
	void preventRemoval() {
		throw new UnsupportedOperationException("Ledger entry " + id + " is append-only");
	}

}
