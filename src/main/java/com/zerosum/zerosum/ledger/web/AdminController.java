package com.zerosum.zerosum.ledger.web;

import com.zerosum.zerosum.common.security.CurrentUserId;
import com.zerosum.zerosum.ledger.service.DepositService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only operations. Every path under /admin requires the ADMIN role (see SecurityConfig). */
@Tag(name = "Admin", description = "Operations for administrators only (ADMIN role)")
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

	private final DepositService depositService;

	@Operation(
			summary = "Deposit money into a user's account",
			description = "Pays the amount into the recipient's account out of TREASURY, which goes negative by "
					+ "exactly that amount; this is how money enters the system. ADMIN role required.\n\n"
					+ "Requires an `Idempotency-Key` header, with the same rules as `POST /transfers`: a repeat with "
					+ "the same key and body replays the original response (`Idempotent-Replayed: true`), and a "
					+ "different body is a 409.")
	@ApiResponse(responseCode = "201", description = "Deposit completed, or replayed for a repeated key",
			headers = @Header(name = IdempotencyHeaders.REPLAYED,
					description = "\"true\" if this response is a replay of an earlier request with the same key",
					schema = @Schema(type = "string", allowableValues = {"true", "false"})),
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = DepositResponse.class)))
	@ApiResponse(responseCode = "400", description = "Validation failed, including a missing or too long "
			+ "Idempotency-Key (errorCode VALIDATION_FAILED); `errors` lists each invalid field",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token (errorCode UNAUTHORIZED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "403", description = "Signed in, but not an admin (errorCode ACCESS_DENIED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "The recipient has no account (errorCode ACCOUNT_NOT_FOUND)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "The Idempotency-Key was already used for a different request "
			+ "(errorCode IDEMPOTENCY_KEY_REUSED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping("/deposits")
	public ResponseEntity<DepositResponse> deposit(
			@CurrentUserId Long adminUserId,
			@Parameter(in = ParameterIn.HEADER, name = IdempotencyHeaders.IDEMPOTENCY_KEY, required = true,
					description = IdempotencyHeaders.KEY_DESCRIPTION, example = IdempotencyHeaders.KEY_EXAMPLE)
			@RequestHeader(name = IdempotencyHeaders.IDEMPOTENCY_KEY, required = false) @NotBlank @Size(max = 255) String idempotencyKey,
			@Valid @RequestBody DepositRequest request) {
		return IdempotencyHeaders.created(depositService.deposit(adminUserId, idempotencyKey, request));
	}

}
