package com.zerosum.zerosum.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;

/**
 * Turns exceptions into RFC 9457 {@link ProblemDetail} responses.
 * <p>
 * Spring MVC's own exceptions (unknown route, unsupported method, unreadable body, ...) keep their
 * standard 4xx mapping from {@link ResponseEntityExceptionHandler}; anything else unexpected becomes
 * a generic 500 so internal details never reach the client.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final String GENERIC_500_DETAIL = "An unexpected error occurred. Please try again later.";

	/** One invalid field (or parameter) and why it was rejected. */
	public record FieldViolation(String field, String message) {
	}

	/** Domain errors. 5xx ones are our bugs, so their detail is logged rather than sent to the client. */
	@ExceptionHandler(ApiException.class)
	public ProblemDetail handleApiException(ApiException ex) {
		String detail = ex.getMessage();
		if (ex.getStatus().is5xxServerError()) {
			log.error("Server-side domain error {}", ex.getErrorCode(), ex);
			detail = GENERIC_500_DETAIL;
		}
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), detail);
		problem.setTitle(ex.getTitle());
		problem.setProperty("errorCode", ex.getErrorCode());
		return problem;
	}

	/** A {@code @Valid @RequestBody} (or other model attribute) failed validation. */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> violations = ex.getBindingResult().getAllErrors().stream()
				.map(error -> new FieldViolation(fieldName(error, error.getObjectName()), error.getDefaultMessage()))
				.toList();
		return handleExceptionInternal(ex, validationProblem(violations), headers, HttpStatus.BAD_REQUEST, request);
	}

	/** A constrained controller method parameter, e.g. {@code @PathVariable @Positive long id}, failed validation. */
	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(
			HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<FieldViolation> violations = ex.getParameterValidationResults().stream()
				.flatMap(result -> result.getResolvableErrors().stream()
						.map(error -> new FieldViolation(
								fieldName(error, result.getMethodParameter().getParameterName()),
								error.getDefaultMessage())))
				.toList();
		return handleExceptionInternal(ex, validationProblem(violations), headers, HttpStatus.BAD_REQUEST, request);
	}

	/** Anything not handled above: log it in full, but tell the client nothing about the cause. */
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) throws Exception {
		// Let Spring Security turn these into 401/403 instead of masking them as 500
		if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
			throw ex;
		}
		log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);

		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, GENERIC_500_DETAIL);
		problem.setTitle("Internal server error");
		problem.setProperty("errorCode", "INTERNAL_ERROR");
		return problem;
	}

	/** The field path for field errors; otherwise (e.g. a class-level constraint) the given fallback name. */
	private static String fieldName(MessageSourceResolvable error, String fallback) {
		return error instanceof FieldError fieldError ? fieldError.getField() : fallback;
	}

	private static ProblemDetail validationProblem(List<FieldViolation> violations) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				HttpStatus.BAD_REQUEST, "The request has " + violations.size() + " invalid field(s)");
		problem.setTitle("Validation failed");
		problem.setProperty("errorCode", "VALIDATION_FAILED");
		problem.setProperty("errors", violations);
		return problem;
	}

}
