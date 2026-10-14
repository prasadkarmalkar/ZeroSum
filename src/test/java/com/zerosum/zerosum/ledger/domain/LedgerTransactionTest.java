package com.zerosum.zerosum.ledger.domain;

import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.exception.LedgerInvariantViolationException;
import com.zerosum.zerosum.ledger.exception.UnbalancedTransactionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The posting rules, as plain unit tests: no Spring context, no database. */
class LedgerTransactionTest {

	private static final Long A = 1L;
	private static final Long B = 2L;
	private static final Long C = 3L;

	// --- valid postings ---

	@Test
	void transferBuildsOneEntryPerLineLinkedToTheTransaction() {
		LedgerTransaction tx = LedgerTransaction.create(TransactionType.TRANSFER, "Rent", List.of(
				line(A, -500, "INR"),
				line(B, 500, "INR")));

		assertThat(tx.getType()).isEqualTo(TransactionType.TRANSFER);
		assertThat(tx.getDescription()).isEqualTo("Rent");
		assertThat(tx.getEntries()).allSatisfy(entry -> assertThat(entry.getTransaction()).isSameAs(tx));
		assertThat(tx.getEntries()).extracting(Entry::getAccountId).containsExactly(A, B);
		assertThat(tx.getEntries()).extracting(Entry::getAmount)
				.containsExactly(Money.of(-500, "INR"), Money.of(500, "INR"));
	}

	@Test
	void allowsMoreThanTwoDistinctAccountsThatSumToZero() {
		LedgerTransaction tx = LedgerTransaction.create(TransactionType.TRANSFER, "Split", List.of(
				line(A, -900, "INR"),
				line(B, 400, "INR"),
				line(C, 500, "INR")));

		assertThat(tx.getEntries()).hasSize(3);
	}

	// --- one case per posting rule, using the rule table's rejected examples ---

	static Stream<Arguments> brokenRules() {
		return Stream.of(
				Arguments.of("at least two entries", List.of(line(A, 500, "INR")),
						"at least two entries"),
				Arguments.of("no zero-amount entries", List.of(line(A, -500, "INR"), line(B, 500, "INR"), line(C, 0, "INR")),
						"zero amount"),
				Arguments.of("one currency", List.of(line(A, -500, "INR"), line(B, 500, "USD")),
						"mix currencies"),
				Arguments.of("each account at most once", List.of(line(A, -500, "INR"), line(A, 500, "INR")),
						"appears in more than one entry"),
				Arguments.of("sum to exactly zero", List.of(line(A, -500, "INR"), line(B, 499, "INR")),
						"sum to -0.01 INR instead of zero"));
	}

	@ParameterizedTest(name = "rule: {0}")
	@MethodSource("brokenRules")
	void everyBrokenRuleThrowsTheLedgerInvariantException(String rule, List<EntryLine> lines, String expectedDetail) {
		assertThatThrownBy(() -> LedgerTransaction.create(TransactionType.TRANSFER, "Bad", lines))
				.isInstanceOf(LedgerInvariantViolationException.class)
				.hasMessageContaining(expectedDetail)
				.satisfies(e -> {
					LedgerInvariantViolationException ex = (LedgerInvariantViolationException) e;
					assertThat(ex.getStatus().value()).isEqualTo(500);
					assertThat(ex.getErrorCode()).isEqualTo("LEDGER_INVARIANT_VIOLATED");
				});
	}

	@Test
	void noLinesAtAllIsRejectedToo() {
		assertThatThrownBy(() -> LedgerTransaction.create(TransactionType.DEPOSIT, "None", List.of()))
				.isInstanceOf(LedgerInvariantViolationException.class);
	}

	@Test
	void unbalancedPostingUsesTheSpecificSubclass() {
		assertThatThrownBy(() -> LedgerTransaction.create(TransactionType.TRANSFER, "Bad", List.of(
				line(A, -500, "INR"), line(B, 499, "INR"))))
				.isInstanceOf(UnbalancedTransactionException.class);
	}

	// --- not posting rules: programming errors in the call itself ---

	@Test
	void rejectsMissingTypeOrBlankDescription() {
		List<EntryLine> lines = List.of(line(A, -1, "INR"), line(B, 1, "INR"));

		assertThatThrownBy(() -> LedgerTransaction.create(null, "x", lines)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> LedgerTransaction.create(TransactionType.TRANSFER, " ", lines))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void entriesCannotBeModifiedThroughTheGetter() {
		LedgerTransaction tx = LedgerTransaction.create(TransactionType.TRANSFER, "Rent", List.of(
				line(A, -500, "INR"), line(B, 500, "INR")));

		assertThatThrownBy(() -> tx.getEntries().clear()).isInstanceOf(UnsupportedOperationException.class);
	}

	private static EntryLine line(Long accountId, long amountMinor, String currency) {
		return new EntryLine(accountId, Money.of(amountMinor, currency));
	}

}
