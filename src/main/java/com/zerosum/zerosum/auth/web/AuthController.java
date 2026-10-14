package com.zerosum.zerosum.auth.web;

import com.zerosum.zerosum.auth.service.AuthService;
import com.zerosum.zerosum.common.web.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "Registration and sign-in")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AuthService authService;

	@Operation(
			summary = "Register a new user",
			description = "Creates a user with the USER role. The email is stored lowercase and must not "
					+ "already be registered (ignoring case). Does not require authentication.")
	@ApiResponse(responseCode = "201", description = "User registered",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = RegisterResponse.class)))
	@ApiResponse(responseCode = "400", description = "Validation failed (errorCode VALIDATION_FAILED); "
			+ "`errors` lists each invalid field",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "409", description = "Email already registered (errorCode EMAIL_ALREADY_REGISTERED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@SecurityRequirements // public endpoint: no authentication required
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
		return RegisterResponse.from(authService.register(request));
	}

	@Operation(
			summary = "Log in",
			description = "Exchanges an email and password for a bearer access token. The email is matched "
					+ "ignoring case. Does not require authentication.")
	@ApiResponse(responseCode = "200", description = "Logged in",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponse.class)))
	@ApiResponse(responseCode = "400", description = "Validation failed (errorCode VALIDATION_FAILED); "
			+ "`errors` lists each invalid field",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@ApiResponse(responseCode = "401", description = "Unknown email or wrong password (errorCode INVALID_CREDENTIALS); "
			+ "the response is the same in both cases",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@SecurityRequirements // public endpoint: no authentication required
	@PostMapping("/login")
	public LoginResponse login(@Valid @RequestBody LoginRequest request) {
		return authService.login(request);
	}

	@Operation(summary = "Get the signed-in user",
			description = "Returns the id, email and role from the caller's access token.")
	@ApiResponse(responseCode = "200", description = "The signed-in user",
			content = @Content(mediaType = "application/json", schema = @Schema(implementation = MeResponse.class)))
	@ApiResponse(responseCode = "401", description = "Missing, invalid or expired access token (errorCode UNAUTHORIZED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ErrorResponse.class)))
	@GetMapping("/me")
	public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
		return MeResponse.from(jwt);
	}

}
