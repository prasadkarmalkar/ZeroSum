package com.zerosum.zerosum.ledger.domain;

import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.exception.UnbalancedTransactionException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreRemove;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

/**
 * A group of {@link Entry entries} that move money between accounts and always sum to zero.
 * <p>
 * Append-only: built once through {@link #create}, never edited or deleted. A mistake is corrected
 * with a new, reversing transaction, the way real accounting works. {@code @Immutable} makes Hibernate
 * ignore changes to a loaded transaction, and {@link #preventRemoval} blocks deletes.
 */
@Entity
@Immutable
@Table(name = "ledger_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // for JPA only
public class LedgerTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TransactionType type;

	@Column(nullable = false)
	private String description;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Getter(AccessLevel.NONE) // exposed read-only via getEntries()
	@OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST)
	@OrderBy("id")
	private List<Entry> entries = new ArrayList<>();

	/**
	 * Builds a transaction and its entries from the given lines.
	 *
	 * @throws IllegalArgumentException      if there are fewer than two lines, a line is zero,
	 *                                       the lines mix currencies, or the description is blank
	 * @throws UnbalancedTransactionException if the lines don't sum to zero
	 */
	public static LedgerTransaction create(TransactionType type, String description, List<EntryLine> lines) {
		Objects.requireNonNull(type, "type must not be null");
		Objects.requireNonNull(description, "description must not be null");
		Objects.requireNonNull(lines, "lines must not be null");
		if (description.isBlank()) {
			throw new IllegalArgumentException("description must not be blank");
		}
		if (lines.size() < 2) {
			throw new IllegalArgumentException("A transaction needs at least two entries, got " + lines.size());
		}

		Currency currency = lines.getFirst().amount().currency();
		Money sum = Money.zero(currency);
		for (EntryLine line : lines) {
			if (line.amount().isZero()) {
				throw new IllegalArgumentException("Entry for account " + line.accountId() + " has a zero amount");
			}
			sum = sum.add(line.amount()); // throws on a currency mismatch
		}
		if (!sum.isZero()) {
			throw new UnbalancedTransactionException(sum);
		}

		LedgerTransaction transaction = new LedgerTransaction();
		transaction.type = type;
		transaction.description = description;
		for (EntryLine line : lines) {
			transaction.entries.add(new Entry(transaction, line.accountId(), line.amount()));
		}
		return transaction;
	}

	public List<Entry> getEntries() {
		return Collections.unmodifiableList(entries);
	}

	@PreRemove
	void preventRemoval() {
		throw new UnsupportedOperationException(
				"Ledger transaction " + id + " is append-only; record a reversing transaction instead");
	}

}
