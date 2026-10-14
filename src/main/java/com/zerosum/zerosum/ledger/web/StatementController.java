package com.zerosum.zerosum.ledger.web;

import com.zerosum.zerosum.common.security.CurrentUserId;
import com.zerosum.zerosum.common.web.ErrorResponse;
import com.zerosum.zerosum.common.web.PageResponse;
import com.zerosum.zerosum.ledger.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lives in the ledger module although the path is under /accounts: a statement is ledger entries, and
 * ledger may depend on account (to find the caller's account) but account may not depend on ledger.
 */
@Tag(name = "Accounts")
@RestController
@RequiredArgsConstructor
public class StatementController {

	private final StatementService statementService;

	@Operation(
			summary = "Get my account statement",
			description = "The signed-in user's ledger entries, newest first, each with the balance right after it. "
					+ "The first row's `balanceAfter` equals the current balance. Rows created at the same instant "
					+ "keep a fixed order, so pages never overlap or skip rows. `size` is capped at "
					+ StatementService.MAX_PAGE_SIZE + "; the response reports the size used.")
	@ApiResponse(responseCode = "200", description = "One page of the statement")
	@ApiResponse(responseCode = "400", description = "page < 0 or size < 1 (errorCode VALIDATION_FAILED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token (errorCode UNAUTHORIZED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "The caller has no account (errorCode ACCOUNT_NOT_FOUND)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@GetMapping("/accounts/me/statement")
	public PageResponse<StatementEntryResponse> statement(
			@CurrentUserId Long userId,
			@Parameter(description = "Zero-based page number", example = "0")
			@RequestParam(defaultValue = "0") @Min(0) int page,
			@Parameter(description = "Rows per page, at most " + StatementService.MAX_PAGE_SIZE, example = "20")
			@RequestParam(defaultValue = "" + StatementService.DEFAULT_PAGE_SIZE) @Min(1) int size) {
		return statementService.statementFor(userId, page, size).map(StatementEntryResponse::from);
	}

}
