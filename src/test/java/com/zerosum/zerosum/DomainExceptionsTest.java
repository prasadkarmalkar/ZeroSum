package com.zerosum.zerosum;

import com.zerosum.zerosum.account.exception.AccountNotFoundException;
import com.zerosum.zerosum.auth.exception.EmailAlreadyRegisteredException;
import com.zerosum.zerosum.auth.exception.InvalidCredentialsException;
import com.zerosum.zerosum.auth.exception.InvalidRefreshTokenException;
import com.zerosum.zerosum.common.ApiException;
import com.zerosum.zerosum.common.Money;
import com.zerosum.zerosum.ledger.exception.IdempotencyKeyReusedException;
import com.zerosum.zerosum.ledger.exception.InsufficientFundsException;
import com.zerosum.zerosum.ledger.exception.SelfTransferNotAllowedException;
import com.zerosum.zerosum.ledger.exception.TransactionNotFoundException;
import com.zerosum.zerosum.ledger.exception.UnbalancedTransactionException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Every domain exception from the README's "Error codes" table, with its expected status and code. */
class DomainExceptionsTest {

	private static final Long ACCOUNT_ID = 42L;

	static Stream<Arguments> exceptions() {
		return Stream.of(
				Arguments.of(new EmailAlreadyRegisteredException("a@b.com"), 409, "EMAIL_ALREADY_REGISTERED"),
				Arguments.of(new InvalidCredentialsException(), 401, "INVALID_CREDENTIALS"),
				Arguments.of(new InvalidRefreshTokenException(), 401, "INVALID_REFRESH_TOKEN"),
				Arguments.of(new AccountNotFoundException(ACCOUNT_ID), 404, "ACCOUNT_NOT_FOUND"),
				Arguments.of(new InsufficientFundsException(ACCOUNT_ID, Money.of(500, "INR"), Money.of(1000, "INR")),
						422, "INSUFFICIENT_FUNDS"),
				Arguments.of(new SelfTransferNotAllowedException(ACCOUNT_ID), 422, "SELF_TRANSFER_NOT_ALLOWED"),
				Arguments.of(new IdempotencyKeyReusedException("key-1"), 409, "IDEMPOTENCY_KEY_REUSED"),
				Arguments.of(new TransactionNotFoundException(17L), 404, "TRANSACTION_NOT_FOUND"),
				Arguments.of(new UnbalancedTransactionException(Money.of(1, "INR")), 500, "LEDGER_INVARIANT_VIOLATED"));
	}

	@ParameterizedTest(name = "{2} -> {1}")
	@MethodSource("exceptions")
	void hasExpectedStatusAndCode(ApiException ex, int expectedStatus, String expectedCode) {
		assertEquals(expectedStatus, ex.getStatus().value());
		assertEquals(expectedCode, ex.getErrorCode());
		assertFalse(ex.getTitle().isBlank());
		assertFalse(ex.getMessage().isBlank());
	}

}
