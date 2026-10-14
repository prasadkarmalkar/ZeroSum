package com.zerosum.zerosum.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes Spring Security's 401 and 403 responses as {@link ProblemDetail} JSON, in the same shape
 * as {@code GlobalExceptionHandler}'s errors (including an {@code errorCode}).
 * <p>
 * It first lets the standard bearer-token handlers set the status and the RFC 6750
 * {@code WWW-Authenticate} header, then adds the body.
 */
@Component
@RequiredArgsConstructor
public class SecurityProblemHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
	private final BearerTokenAccessDeniedHandler bearerAccessDeniedHandler = new BearerTokenAccessDeniedHandler();
	private final JsonMapper jsonMapper;

	/** 401: no token, or a token that is malformed, expired or wrongly signed. */
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException ex) throws IOException {
		bearerEntryPoint.commence(request, response, ex);
		write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required",
				"A valid bearer access token is required");
	}

	/** 403: a valid token, but its roles don't allow this request. */
	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException ex) throws IOException {
		bearerAccessDeniedHandler.handle(request, response, ex);
		write(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Access denied",
				"You do not have permission to perform this action");
	}

	private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
			String errorCode, String title, String detail) throws IOException {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(title);
		problem.setInstance(URI.create(request.getRequestURI()));

		// Flattened by hand, as Spring MVC's ProblemDetail mix-in would, so errorCode is top-level
		Map<String, Object> body = new LinkedHashMap<>();
		// Spring 7 leaves type null by default; RFC 9457 says that means "about:blank"
		body.put("type", problem.getType() != null ? problem.getType().toString() : "about:blank");
		body.put("title", problem.getTitle());
		body.put("status", problem.getStatus());
		body.put("detail", problem.getDetail());
		body.put("instance", problem.getInstance().toString());
		body.put("errorCode", errorCode);

		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), body);
	}

}
