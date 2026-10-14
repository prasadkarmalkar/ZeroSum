package com.zerosum.zerosum.ledger.web;

import com.zerosum.zerosum.common.security.CurrentUserId;
import com.zerosum.zerosum.ledger.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
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
					+ "always the caller, identified by the access token; the request has no sender field.")
	@ApiResponse(responseCode = "201", description = "Transfer completed",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransferResponse.class)))
	@ApiResponse(responseCode = "400", description = "Validation failed (errorCode VALIDATION_FAILED); "
			+ "`errors` lists each invalid field",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token (errorCode UNAUTHORIZED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "The recipient (or the caller) has no account (errorCode ACCOUNT_NOT_FOUND)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "Not enough money (errorCode INSUFFICIENT_FUNDS), "
			+ "or sending to yourself (errorCode SELF_TRANSFER_NOT_ALLOWED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TransferResponse transfer(@CurrentUserId Long userId, @Valid @RequestBody TransferRequest request) {
		return transferService.transfer(userId, request);
	}

}
