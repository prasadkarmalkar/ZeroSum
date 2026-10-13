package com.zerosum.zerosum.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

	private static class TestException extends ApiException {
		TestException() {
			super(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "Account not found", "Account 42 does not exist");
		}
	}

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void mapsApiExceptionToProblemDetail() {
		ProblemDetail problem = handler.handleApiException(new TestException());

		assertEquals(404, problem.getStatus());
		assertEquals("Account not found", problem.getTitle());
		assertEquals("Account 42 does not exist", problem.getDetail());
		assertEquals(Map.of("errorCode", "ACCOUNT_NOT_FOUND"), problem.getProperties());
	}

}
