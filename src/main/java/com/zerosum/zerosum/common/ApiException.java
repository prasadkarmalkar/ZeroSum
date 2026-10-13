package com.zerosum.zerosum.common;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * Base class for exceptions that map to an HTTP error response.
 * Each subclass supplies the HTTP status, a stable machine-readable error code
 * (e.g. {@code ACCOUNT_NOT_FOUND}) and a short human-readable title. The exception
 * message is the detail for this specific occurrence.
 */
@Getter
@Setter
public abstract class ApiException extends RuntimeException {

	private final HttpStatus status;
	private final String errorCode;
	private final String title;

	protected ApiException(HttpStatus status, String errorCode, String title, String detail) {
		this(status, errorCode, title, detail, null);
	}

	protected ApiException(HttpStatus status, String errorCode, String title, String detail, Throwable cause) {
		super(detail, cause);
		this.status = Objects.requireNonNull(status, "status must not be null");
		this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
		this.title = Objects.requireNonNull(title, "title must not be null");
	}
}
