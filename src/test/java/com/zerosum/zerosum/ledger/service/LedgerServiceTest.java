package com.zerosum.zerosum.ledger.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.domain.EntryLine;
import com.zerosum.zerosum.ledger.domain.LedgerTransaction;
import com.zerosum.zerosum.ledger.domain.TransactionType;
import com.zerosum.zerosum.ledger.exception.LedgerInvariantViolationException;
import com.zerosum.zerosum.ledger.repository.LedgerTransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Plain Mockito, no Spring context. */
class LedgerServiceTest {

	private final LedgerTransactionRepository repository = mock(LedgerTransactionRepository.class);
	private final LedgerService service = new LedgerService(repository);

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
	void validPostingIsSavedAndItsIdReturned() {
		when(repository.save(any())).thenAnswer(invocation -> {
			LedgerTransaction tx = invocation.getArgument(0);
			ReflectionTestUtils.setField(tx, "id", 42L);
			return tx;
		});

		Long id = service.post(TransactionType.TRANSFER, "Rent", List.of(
				new EntryLine(1L, Money.of(-500, "INR")),
				new EntryLine(2L, Money.of(500, "INR"))));

		assertThat(id).isEqualTo(42L);
	}

	@Test
	void invalidPostingIsNeverSavedAndIsLoggedAtErrorLevel() {
		assertThatThrownBy(() -> service.post(TransactionType.TRANSFER, "Bad", List.of(
				new EntryLine(1L, Money.of(-500, "INR")),
				new EntryLine(2L, Money.of(499, "INR")))))
				.isInstanceOf(LedgerInvariantViolationException.class);

		verify(repository, never()).save(any());
		assertThat(logs.list).anySatisfy(event -> {
			assertThat(event.getLevel()).isEqualTo(Level.ERROR);
			assertThat(event.getFormattedMessage())
					.contains("Rejected TRANSFER posting \"Bad\"")
					.contains("sum to -0.01 INR instead of zero");
		});
	}

}
