package com.zerosum.zerosum.auth.web;

import org.springframework.core.io.Resource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT settings, bound from the {@code jwt.*} properties.
 *
 * @param issuer          value of the token's "iss" claim, e.g. "zerosum"
 * @param accessTokenTtl  how long an access token stays valid, e.g. 15m
 * @param privateKey      location of the PKCS#8 PEM private key used to sign tokens
 * @param publicKey       location of the X.509 PEM public key used to verify tokens
 */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JWTProperty(
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Resource privateKey,
        @NotNull Resource publicKey
) {
}
