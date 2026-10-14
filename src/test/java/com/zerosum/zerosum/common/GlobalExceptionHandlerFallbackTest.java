package com.zerosum.zerosum.common;

import com.zerosum.zerosum.ledger.exception.InsufficientFundsException;
import com.zerosum.zerosum.ledger.exception.UnbalancedTransactionException;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerFallbackTest {

	record Body(String name) {
	}

	@RestController
	static class TestController {

		@GetMapping("/boom")
		void boom() {
			throw new IllegalStateException("secret connection string jdbc:postgresql://db");
		}

		@GetMapping("/forbidden")
		void forbidden() {
			throw new AccessDeniedException("nope");
		}

		@PostMapping("/things")
		void create(@RequestBody Body body) {
		}

		@PostMapping("/transfers")
		void transfer() {
			throw new InsufficientFundsException(ACCOUNT_ID, Money.of(500, "INR"), Money.of(1000, "INR"));
		}

		@PostMapping("/transfers/unbalanced")
		void unbalancedTransfer() {
			throw new UnbalancedTransactionException(Money.of(1, "INR"));
		}

	}

	private static final Long ACCOUNT_ID = 42L;

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void unexpectedExceptionBecomesGeneric500WithoutLeakingDetails() throws Exception {
		mockMvc.perform(get("/boom"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.title").value("Internal server error"))
				.andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
				.andExpect(content().string(not(containsString("secret"))));
	}

	@Test
	void domainExceptionReturnsItsProblemDetail() throws Exception {
		mockMvc.perform(post("/transfers"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(422))
				.andExpect(jsonPath("$.title").value("Insufficient funds"))
				.andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FUNDS"))
				.andExpect(jsonPath("$.detail").value(
						"Account 42 has 5.00 INR available but 10.00 INR was requested"));
	}

	@Test
	void serverSideDomainExceptionHidesItsDetail() throws Exception {
		mockMvc.perform(post("/transfers/unbalanced"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.errorCode").value("LEDGER_INVARIANT_VIOLATED"))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
				.andExpect(content().string(not(containsString("sum to"))));
	}

	@Test
	void malformedJsonStillReturns400() throws Exception {
		mockMvc.perform(post("/things").contentType(MediaType.APPLICATION_JSON).content("{not json"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unsupportedMethodStillReturns405() throws Exception {
		mockMvc.perform(post("/boom"))
				.andExpect(status().isMethodNotAllowed());
	}

	@Test
	void securityExceptionsAreLeftForSpringSecurity() {
		ServletException thrown = assertThrows(ServletException.class, () -> mockMvc.perform(get("/forbidden")));
		assertInstanceOf(AccessDeniedException.class, thrown.getCause());
	}

}
