package com.zerosum.zerosum.ledger.web;

import com.zerosum.zerosum.common.security.CurrentUserId;
import com.zerosum.zerosum.common.web.ErrorResponse;
import com.zerosum.zerosum.ledger.service.TransferService;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transfers", description = "Send money to another user")
@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

	private final TransferService transferService;

	@Operation(
			summary = "Send money to another user",
			description = "Moves the amount from the signed-in user's account to the recipient's. The sender is "
					+ "always the caller, identified by the access token; the request has no sender field.\n\n"
					+ "Requires an `Idempotency-Key` header (e.g. a fresh UUID per transfer). Retrying with the same "
					+ "key and the same body never sends the money twice: it returns the original 201 response "
					+ "again, with `Idempotent-Replayed: true`. Reusing a key with a different body is a 409. "
					+ "Failed requests aren't remembered, so a key can be retried after fixing the problem.")
	@ApiResponse(responseCode = "201", description = "Transfer completed, or replayed for a repeated key",
			headers = @Header(name = IdempotencyHeaders.REPLAYED, description = "\"true\" if this response is a replay of an earlier request with the same key",
					schema = @Schema(type = "string", allowableValues = {"true", "false"})),
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransferResponse.class)))
	@ApiResponse(responseCode = "400", description = "Validation failed, including a missing or too long "
			+ "Idempotency-Key (errorCode VALIDATION_FAILED); `errors` lists each invalid field",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token (errorCode UNAUTHORIZED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "The recipient (or the caller) has no account (errorCode ACCOUNT_NOT_FOUND)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "The Idempotency-Key was already used for a different request "
			+ "(errorCode IDEMPOTENCY_KEY_REUSED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "422", description = "Not enough money (errorCode INSUFFICIENT_FUNDS), "
			+ "or sending to yourself (errorCode SELF_TRANSFER_NOT_ALLOWED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@PostMapping
	public ResponseEntity<TransferResponse> transfer(
			@CurrentUserId Long userId,
			@Parameter(in = ParameterIn.HEADER, name = IdempotencyHeaders.IDEMPOTENCY_KEY, required = true,
					description = IdempotencyHeaders.KEY_DESCRIPTION, example = IdempotencyHeaders.KEY_EXAMPLE)
			@RequestHeader(name = IdempotencyHeaders.IDEMPOTENCY_KEY, required = false) @NotBlank @Size(max = 255) String idempotencyKey,
			@Valid @RequestBody TransferRequest request) {
		return IdempotencyHeaders.created(transferService.transfer(userId, idempotencyKey, request));
	}

}
