package com.zerosum.zerosum.ledger.domain;

import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.exception.LedgerInvariantViolationException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
	 * Builds a transaction and its entries from the given lines, enforcing every posting rule.
	 * This is the only place the rules live, so an invalid transaction can't exist even in memory:
	 * <ol>
	 *   <li>at least two entries</li>
	 *   <li>no zero-amount entries</li>
	 *   <li>all entries in one currency</li>
	 *   <li>each account appears at most once</li>
	 *   <li>entries sum to exactly zero</li>
	 * </ol>
	 *
	 * @throws LedgerInvariantViolationException if any rule is broken ({@link UnbalancedTransactionException}
	 *                                           for the last one); always a 500, since callers must never
	 *                                           build an invalid posting
	 * @throws NullPointerException              if type, description, lines or a line is null
	 * @throws IllegalArgumentException          if the description is blank
	 */
	public static LedgerTransaction create(TransactionType type, String description, List<EntryLine> lines) {
		Objects.requireNonNull(type, "type must not be null");
		Objects.requireNonNull(description, "description must not be null");
		Objects.requireNonNull(lines, "lines must not be null");
		if (description.isBlank()) {
			throw new IllegalArgumentException("description must not be blank");
		}

		requireAtLeastTwoEntries(lines);
		requireNoZeroAmounts(lines);
		requireSingleCurrency(lines);
		requireDistinctAccounts(lines);
		requireSumOfZero(lines);

		LedgerTransaction transaction = new LedgerTransaction();
		transaction.type = type;
		transaction.description = description;
		for (EntryLine line : lines) {
			transaction.entries.add(new Entry(transaction, line.accountId(), line.amount()));
		}
		return transaction;
	}

	private static void requireAtLeastTwoEntries(List<EntryLine> lines) {
		if (lines.size() < 2) {
			throw new LedgerInvariantViolationException(
					"A transaction needs at least two entries, got " + lines.size());
		}
	}

	private static void requireNoZeroAmounts(List<EntryLine> lines) {
		for (EntryLine line : lines) {
			if (line.amount().isZero()) {
				throw new LedgerInvariantViolationException(
						"Entry for account " + line.accountId() + " has a zero amount");
			}
		}
	}

	private static void requireSingleCurrency(List<EntryLine> lines) {
		Currency currency = lines.getFirst().amount().currency();
		for (EntryLine line : lines) {
			if (!line.amount().currency().equals(currency)) {
				throw new LedgerInvariantViolationException(
						"Entries mix currencies: " + currency + " and " + line.amount().currency());
			}
		}
	}

	private static void requireDistinctAccounts(List<EntryLine> lines) {
		Set<Long> seen = new HashSet<>();
		for (EntryLine line : lines) {
			if (!seen.add(line.accountId())) {
				throw new LedgerInvariantViolationException(
						"Account " + line.accountId() + " appears in more than one entry");
			}
		}
	}

	private static void requireSumOfZero(List<EntryLine> lines) {
		Money sum = Money.zero(lines.getFirst().amount().currency());
		for (EntryLine line : lines) {
			sum = sum.add(line.amount());
		}
		if (!sum.isZero()) {
			throw new UnbalancedTransactionException(sum);
		}
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
