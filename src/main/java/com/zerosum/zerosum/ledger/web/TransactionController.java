package com.zerosum.zerosum.ledger.web;

import com.zerosum.zerosum.common.security.CurrentUserId;
import com.zerosum.zerosum.common.web.ErrorResponse;
import com.zerosum.zerosum.ledger.service.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts")
@RestController
@RequiredArgsConstructor
public class TransactionController {

	private final TransactionQueryService transactionQueryService;

	@Operation(
			summary = "Get one of my transactions",
			description = "A transaction the signed-in user sent or received, with only their own signed amount. "
					+ "Anyone else's transaction gives the same 404 as an id that doesn't exist, so ids can't be "
					+ "probed.")
	@ApiResponse(responseCode = "200", description = "The transaction, from the caller's side",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionResponse.class)))
	@ApiResponse(responseCode = "400", description = "The id isn't a positive whole number (errorCode VALIDATION_FAILED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token (errorCode UNAUTHORIZED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "No such transaction, or the caller isn't part of it; "
			+ "the two are indistinguishable (errorCode TRANSACTION_NOT_FOUND)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@GetMapping("/accounts/me/transactions/{id}")
	public TransactionResponse transaction(
			@CurrentUserId Long userId,
			@Parameter(description = "The transaction id", example = "17") @PathVariable @Positive Long id) {
		return transactionQueryService.getForUser(userId, id);
	}

}
