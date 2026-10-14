package com.zerosum.zerosum.ledger.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zerosum.zerosum.account.domain.AccountType;
import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.account.service.AccountView;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.LedgerTransaction;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.exception.InsufficientFundsException;
import com.zerosum.zerosum.ledger.exception.LedgerInvariantViolationException;
import com.zerosum.zerosum.ledger.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Currency;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Plain Mockito, no Spring context. */
class LedgerServiceTest {

	private static final Currency INR = Currency.getInstance("INR");

	private final LedgerTransactionRepository repository = mock(LedgerTransactionRepository.class);
	private final AccountService accountService = mock(AccountService.class);
	private final LedgerService service = new LedgerService(repository, accountService);

	private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
	private final Logger logger = (Logger) LoggerFactory.getLogger(LedgerService.class);

	@BeforeEach
	void captureLogs() {
		logs.start();
		logger.addAppender(logs);
	}

	@AfterEach
	void stopCapturingLogs() {
		logger.detachAppender(logs);
	}

	@Test
	void validPostingLocksThenSavesThenAppliesBalancesAndReturnsTheId() {
		when(accountService.lockAccounts(anyCollection()))
				.thenReturn(List.of(view(1L, INR, 1000), view(2L, INR, 0)));
		when(repository.save(any())).thenAnswer(invocation -> {
			LedgerTransaction tx = invocation.getArgument(0);
			ReflectionTestUtils.setField(tx, "id", 42L);
			return tx;
		});

		Long id = service.post(TransactionType.TRANSFER, "Rent", List.of(
				new EntryLine(1L, Money.of(-500, "INR")),
				new EntryLine(2L, Money.of(500, "INR"))));

		assertThat(id).isEqualTo(42L);
		var order = inOrder(accountService, repository);
		order.verify(accountService).lockAccounts(List.of(1L, 2L));
		order.verify(repository).save(any());
		order.verify(accountService).applyBalanceChanges(Map.of(1L, Money.of(-500, "INR"), 2L, Money.of(500, "INR")));
	}

	@Test
	void overdraftIsRejectedBeforeAnythingIsSaved() {
		when(accountService.lockAccounts(anyCollection()))
				.thenReturn(List.of(view(1L, INR, 499), view(2L, INR, 0)));

		assertThatThrownBy(() -> service.post(TransactionType.TRANSFER, "Too much", List.of(
				new EntryLine(1L, Money.of(-500, "INR")),
				new EntryLine(2L, Money.of(500, "INR")))))
				.isInstanceOf(InsufficientFundsException.class)
				.hasMessage("Account 1 has 4.99 INR available but 5.00 INR was requested");

		verify(repository, never()).save(any());
		verify(accountService, never()).applyBalanceChanges(anyMap());
	}

	@Test
	void systemAccountsAreExemptFromTheOverdraftCheck() {
		when(accountService.lockAccounts(anyCollection())).thenReturn(List.of(
				new AccountView(1L, AccountType.SYSTEM, INR, new Money(0, INR)), view(2L, INR, 0)));
		when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

		service.post(TransactionType.DEPOSIT, "Deposit", List.of(
				new EntryLine(1L, Money.of(-500, "INR")),
				new EntryLine(2L, Money.of(500, "INR"))));

		verify(accountService).applyBalanceChanges(anyMap());
	}

	@Test
	void brokenRuleIsNeverLockedOrSavedAndIsLoggedAtErrorLevel() {
		assertThatThrownBy(() -> service.post(TransactionType.TRANSFER, "Bad", List.of(
				new EntryLine(1L, Money.of(-500, "INR")),
				new EntryLine(2L, Money.of(499, "INR")))))
				.isInstanceOf(LedgerInvariantViolationException.class);

		verifyNoInteractions(accountService);
		verify(repository, never()).save(any());
		assertErrorLogged("sum to -0.01 INR instead of zero");
	}

	@Test
	void entryCurrencyMustMatchItsAccount() {
		when(accountService.lockAccounts(anyCollection()))
				.thenReturn(List.of(view(1L, INR, 1000), view(2L, INR, 0)));

		assertThatThrownBy(() -> service.post(TransactionType.TRANSFER, "USD into INR accounts", List.of(
				new EntryLine(1L, Money.of(-500, "USD")),
				new EntryLine(2L, Money.of(500, "USD")))))
				.isInstanceOf(LedgerInvariantViolationException.class)
				.hasMessageContaining("is in USD but the account is in INR");

		verify(repository, never()).save(any());
		assertErrorLogged("is in USD but the account is in INR");
	}

	@Test
	void missingAccountIsReportedAndNothingIsSaved() {
		when(accountService.lockAccounts(anyCollection())).thenThrow(new AccountNotFoundException(2L));

		assertThatThrownBy(() -> service.post(TransactionType.TRANSFER, "To nowhere", List.of(
				new EntryLine(1L, Money.of(-500, "INR")),
				new EntryLine(2L, Money.of(500, "INR")))))
				.isInstanceOf(AccountNotFoundException.class);

		verify(repository, never()).save(any());
	}

	private void assertErrorLogged(String text) {
		assertThat(logs.list).anySatisfy(event -> {
			assertThat(event.getLevel()).isEqualTo(Level.ERROR);
			assertThat(event.getFormattedMessage()).contains("Rejected TRANSFER posting").contains(text);
		});
	}

	private static AccountView view(Long id, Currency currency, long balanceMinor) {
		return new AccountView(id, AccountType.USER, currency, new Money(balanceMinor, currency));
	}

}
