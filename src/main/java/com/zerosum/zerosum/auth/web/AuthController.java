package com.zerosum.zerosum.auth.web;

import com.zerosum.zerosum.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
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
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "Email already registered (errorCode EMAIL_ALREADY_REGISTERED)",
			content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@SecurityRequirements // public endpoint: no authentication required
	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public RegisterResponse register(@Valid @RequestBody RegisterRequest request) {
		return RegisterResponse.from(authService.register(request));
	}

}
