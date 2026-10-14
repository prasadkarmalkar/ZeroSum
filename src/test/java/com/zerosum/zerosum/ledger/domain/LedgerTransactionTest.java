package com.zerosum.zerosum.ledger.domain;

import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.exception.UnbalancedTransactionException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LedgerTransactionTest {

	private static final Long A = 1L;
	private static final Long B = 2L;

	@Test
	void transferBuildsOneEntryPerLineLinkedToTheTransaction() {
		LedgerTransaction tx = LedgerTransaction.create(TransactionType.TRANSFER, "Rent", List.of(
				new EntryLine(A, Money.of(-500, "INR")),
				new EntryLine(B, Money.of(500, "INR"))));

		assertThat(tx.getType()).isEqualTo(TransactionType.TRANSFER);
		assertThat(tx.getDescription()).isEqualTo("Rent");
		assertThat(tx.getEntries()).hasSize(2);
		assertThat(tx.getEntries()).allSatisfy(entry -> assertThat(entry.getTransaction()).isSameAs(tx));
		assertThat(tx.getEntries()).extracting(Entry::getAccountId).containsExactly(A, B);
		assertThat(tx.getEntries()).extracting(Entry::getAmount)
				.containsExactly(Money.of(-500, "INR"), Money.of(500, "INR"));
	}

	@Test
	void allowsMoreThanTwoLinesThatSumToZero() {
		LedgerTransaction tx = LedgerTransaction.create(TransactionType.TRANSFER, "Split", List.of(
				new EntryLine(A, Money.of(-900, "INR")),
				new EntryLine(B, Money.of(400, "INR")),
				new EntryLine(3L, Money.of(500, "INR"))));

		assertThat(tx.getEntries()).hasSize(3);
	}

	@Test
	void rejectsLinesThatDontSumToZero() {
		assertThatThrownBy(() -> LedgerTransaction.create(TransactionType.TRANSFER, "Oops", List.of(
				new EntryLine(A, Money.of(-500, "INR")),
				new EntryLine(B, Money.of(499, "INR")))))
				.isInstanceOf(UnbalancedTransactionException.class)
				.hasMessageContaining("-0.01 INR");
	}

	@Test
	void rejectsZeroAmountEntry() {
		assertThatThrownBy(() -> LedgerTransaction.create(TransactionType.TRANSFER, "Zero", List.of(
				new EntryLine(A, Money.of(-500, "INR")),
				new EntryLine(B, Money.of(500, "INR")),
				new EntryLine(3L, Money.of(0, "INR")))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("zero");
	}

	@Test
	void rejectsFewerThanTwoLines() {
		assertThatThrownBy(() -> LedgerTransaction.create(TransactionType.DEPOSIT, "One", List.of(
				new EntryLine(A, Money.of(500, "INR")))))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> LedgerTransaction.create(TransactionType.DEPOSIT, "None", List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsMixedCurrencies() {
		assertThatThrownBy(() -> LedgerTransaction.create(TransactionType.TRANSFER, "Mixed", List.of(
				new EntryLine(A, Money.of(-500, "INR")),
				new EntryLine(B, Money.of(500, "USD")))))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Currency mismatch");
	}

	@Test
	void rejectsMissingTypeOrBlankDescription() {
		List<EntryLine> lines = List.of(new EntryLine(A, Money.of(-1, "INR")), new EntryLine(B, Money.of(1, "INR")));

		assertThatThrownBy(() -> LedgerTransaction.create(null, "x", lines)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> LedgerTransaction.create(TransactionType.TRANSFER, " ", lines))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void entriesCannotBeModifiedThroughTheGetter() {
		LedgerTransaction tx = LedgerTransaction.create(TransactionType.TRANSFER, "Rent", List.of(
				new EntryLine(A, Money.of(-500, "INR")),
				new EntryLine(B, Money.of(500, "INR"))));

		assertThatThrownBy(() -> tx.getEntries().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

}
