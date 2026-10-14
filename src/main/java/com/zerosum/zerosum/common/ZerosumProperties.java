package com.zerosum.zerosum.common;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Currency;

/**
 * Application-wide settings, bound from the {@code zerosum.*} properties.
 *
 * @param currency the single currency every account and amount uses, as an ISO 4217 code, e.g. INR
 */
@Validated
@ConfigurationProperties(prefix = "zerosum")
public record ZerosumProperties(@NotNull Currency currency) {
}
