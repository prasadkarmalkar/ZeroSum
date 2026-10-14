package com.zerosum.zerosum.account.web;

import com.zerosum.zerosum.account.service.AccountService;
import com.zerosum.zerosum.common.security.CurrentUserId;
import com.zerosum.zerosum.common.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Accounts", description = "The signed-in user's account and balance")
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

	private final AccountService accountService;

	@Operation(
			summary = "Get my account",
			description = "Returns the signed-in user's account. The user is identified by the access token only; "
					+ "there is no way to ask for another user's account.")
	@ApiResponse(responseCode = "200", description = "The caller's account",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = AccountResponse.class)))
	@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token (errorCode UNAUTHORIZED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "404", description = "The caller has no account (errorCode ACCOUNT_NOT_FOUND)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@GetMapping("/me")
	public AccountResponse me(@CurrentUserId Long userId) {
		return AccountResponse.from(accountService.getForUser(userId));
	}

}
