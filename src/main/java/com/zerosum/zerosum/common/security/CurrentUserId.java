package com.zerosum.zerosum.common.security;

import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the signed-in user's id, taken from the verified access token's {@code sub} claim:
 * {@code public AccountResponse me(@CurrentUserId Long userId)}.
 * <p>
 * Who the caller is always comes from the token, never from the request (path, query or body),
 * so a user can't ask for someone else's data by changing an id. It lives in common so any
 * module can use it without depending on auth, and it is hidden from the OpenAPI docs.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "T(java.lang.Long).valueOf(subject)", errorOnInvalidType = true)
@Parameter(hidden = true)
public @interface CurrentUserId {
}
