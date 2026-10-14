package com.zerosum.zerosum.common;

import com.zerosum.zerosum.common.web.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

	private static final String BEARER_AUTH = "bearerAuth";
	private static final String PROBLEM_JSON = "application/problem+json";

	private static final String DESCRIPTION = """
			A zero-sum money ledger: money is never created or lost. Every transfer is a set of ledger \
			entries that sum to zero, and only an admin deposit, paid out of the TREASURY account, brings \
			money in.

			**Try the whole API from this page:**

			1. **Log in as the admin.** `POST /auth/login` with the seeded admin. Locally that's \
			`admin@zerosum.local` / `admin-dev-Passw0rd`, unless `ADMIN_EMAIL` / `ADMIN_PASSWORD` were set. \
			Copy `accessToken`, click **Authorize**, and paste it.
			2. **Create two users.** `POST /auth/register` twice, e.g. `alice@example.com` and `bob@example.com`.
			3. **Fund Alice.** `POST /admin/deposits` with `recipientEmail` alice and an `Idempotency-Key` (any new \
			UUID). Check `GET /admin/trial-balance`: still balanced.
			4. **Become Alice.** `POST /auth/login` as Alice, click **Authorize** again, paste her token.
			5. **Send money.** `POST /transfers` to bob, with a new `Idempotency-Key`. Send it again with the same \
			key: you get the same response, `Idempotent-Replayed: true`, and no second transfer.
			6. **Look around.** `GET /accounts/me`, `GET /accounts/me/statement`, \
			`GET /accounts/me/transactions/{id}`.

			**Conventions**
			- Amounts are decimal strings (`"12.50"`), never JSON numbers.
			- Every error is `application/problem+json` with a stable `errorCode`; branch on that, not on the text.
			- Money-moving requests require an `Idempotency-Key` header, so retries are always safe.
			""";

	/**
	 * Every error code's example body, attached to each response whose description names the code
	 * (e.g. "(errorCode INSUFFICIENT_FUNDS)"), so descriptions and examples can't drift apart.
	 */
	private static final Map<String, Map<String, Object>> ERROR_EXAMPLES = new LinkedHashMap<>();

	static {
		error("VALIDATION_FAILED", 400, "Validation failed", "The request has 1 invalid field(s)", "/transfers",
				List.of(Map.of("field", "amount", "message", "must be at least 0.01")));
		error("UNAUTHORIZED", 401, "Authentication required", "A valid bearer access token is required", "/accounts/me", null);
		error("INVALID_CREDENTIALS", 401, "Invalid credentials", "The email or password is incorrect", "/auth/login", null);
		error("ACCESS_DENIED", 403, "Access denied", "You do not have permission to perform this action", "/admin/deposits", null);
		error("ACCOUNT_NOT_FOUND", 404, "Account not found", "No account found for 'nobody@example.com'", "/transfers", null);
		error("TRANSACTION_NOT_FOUND", 404, "Transaction not found", "Transaction 17 was not found", "/accounts/me/transactions/17", null);
		error("EMAIL_ALREADY_REGISTERED", 409, "Email already registered",
				"An account with email 'alice@example.com' already exists", "/auth/register", null);
		error("IDEMPOTENCY_KEY_REUSED", 409, "Idempotency key reused",
				"Idempotency key '3f1c2e9a-8b7d-4c1e-9f0a-6d2b5e8c4a71' was already used for a different request", "/transfers", null);
		error("INSUFFICIENT_FUNDS", 422, "Insufficient funds",
				"Account 3 has 100.00 INR available but 150.00 INR was requested", "/transfers", null);
		error("SELF_TRANSFER_NOT_ALLOWED", 422, "Self-transfer not allowed", "Cannot transfer from account 3 to itself", "/transfers", null);
	}

	private static void error(String code, int status, String title, String detail, String instance, Object errors) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("type", "about:blank");
		body.put("title", title);
		body.put("status", status);
		body.put("detail", detail);
		body.put("instance", instance);
		body.put("errorCode", code);
		if (errors != null) {
			body.put("errors", errors);
		}
		ERROR_EXAMPLES.put(code, body);
	}

	/**
	 * Every operation requires a bearer token by default, which enables Swagger UI's Authorize button.
	 * Public operations opt out with an empty {@code @SecurityRequirements}.
	 */
	@Bean
	public OpenAPI zerosumOpenApi() {
		Components components = new Components()
				.addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")
						.description("Paste an access token from POST /auth/login (without the \"Bearer \" prefix)."));
		ModelConverters.getInstance().readAll(ErrorResponse.class).forEach(components::addSchemas);
		ERROR_EXAMPLES.forEach((code, body) -> components.addExamples(code, new Example().summary(code).value(body)));

		return new OpenAPI()
				.info(new Info()
						.title("Zerosum API")
						.description(DESCRIPTION)
						.version("v1"))
				.components(components)
				.tags(List.of(
						new Tag().name("Auth").description("Register, log in, and see who you are"),
						new Tag().name("Accounts").description("Your account, balance, statement and transactions"),
						new Tag().name("Transfers").description("Send money to another user"),
						new Tag().name("Admin").description("Admin only: deposits and the trial balance")))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

	/** Gives every error response the {@link ErrorResponse} schema and an example for each errorCode it names. */
	@Bean
	public OpenApiCustomizer errorExamplesCustomizer() {
		return openApi -> openApi.getPaths().values().forEach(path -> path.readOperations().forEach(operation -> {
			if (operation.getResponses() == null) {
				return;
			}
			operation.getResponses().forEach((status, response) -> {
				if (status.startsWith("4") || status.startsWith("5")) {
					attachErrorExamples(response);
				}
			});
		}));
	}

	private static void attachErrorExamples(ApiResponse response) {
		String description = response.getDescription() == null ? "" : response.getDescription();
		MediaType mediaType = new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"));
		ERROR_EXAMPLES.keySet().stream()
				.filter(description::contains)
				.forEach(code -> mediaType.addExamples(code, new Example().$ref("#/components/examples/" + code)));
		response.setContent(new Content().addMediaType(PROBLEM_JSON, mediaType));
	}

}
