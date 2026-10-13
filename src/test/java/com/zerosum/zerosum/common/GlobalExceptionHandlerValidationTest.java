package com.zerosum.zerosum.common;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerValidationTest {

	record CreateRequest(@NotBlank String name, @Positive long amountMinor) {
	}

	@RestController
	static class TestController {

		@PostMapping("/things")
		void create(@Valid @RequestBody CreateRequest request) {
		}

		@GetMapping("/things/{id}")
		void get(@PathVariable @Positive long id) {
		}

	}

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

	@Test
	void invalidRequestBodyListsEachField() throws Exception {
		mockMvc.perform(post("/things")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"name\": \"\", \"amountMinor\": -5}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors", hasSize(2)))
				.andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("name", "amountMinor")))
				.andExpect(jsonPath("$.errors[*].message",
						containsInAnyOrder("must not be blank", "must be greater than 0")));
	}

	@Test
	void invalidPathVariableIsListedByParameterName() throws Exception {
		mockMvc.perform(get("/things/-1"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("id"))
				.andExpect(jsonPath("$.errors[0].message").value("must be greater than 0"));
	}

}
