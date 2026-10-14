package com.zerosum.zerosum.auth.web;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestTest {

	private static final String VALID_PASSWORD = "s3cret-Passw0rd";

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void validRequestHasNoViolations() {
		assertThat(validator.validate(new RegisterRequest("alice@example.com", VALID_PASSWORD))).isEmpty();
	}

	@ParameterizedTest(name = "email \"{0}\" is rejected")
	@CsvSource(value = {"NULL", "''", "'   '", "not-an-email"}, nullValues = "NULL")
	void rejectsInvalidEmail(String email) {
		assertThat(invalidFields(new RegisterRequest(email, VALID_PASSWORD))).containsExactly("email");
	}

	@Test
	void rejectsEmailLongerThanColumn() {
		String tooLong = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(63) + ".com";

		assertThat(invalidFields(new RegisterRequest(tooLong, VALID_PASSWORD))).containsExactly("email");
	}

	@Test
	void rejectsMissingShortAndTooLongPasswords() {
		assertThat(invalidFields(new RegisterRequest("alice@example.com", null))).containsExactly("password");
		assertThat(invalidFields(new RegisterRequest("alice@example.com", "short"))).containsExactly("password");
		assertThat(invalidFields(new RegisterRequest("alice@example.com", "x".repeat(73)))).containsExactly("password");
		assertThat(invalidFields(new RegisterRequest("alice@example.com", "x".repeat(72)))).isEmpty();
	}

	@Test
	void toStringHidesPassword() {
		assertThat(new RegisterRequest("alice@example.com", VALID_PASSWORD).toString())
				.contains("alice@example.com")
				.doesNotContain(VALID_PASSWORD);
	}

	private Set<String> invalidFields(RegisterRequest request) {
		return validator.validate(request).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.collect(Collectors.toSet());
	}

}
