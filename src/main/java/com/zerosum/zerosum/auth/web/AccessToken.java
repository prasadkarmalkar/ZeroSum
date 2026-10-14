package com.zerosum.zerosum.auth.web;

import java.time.Instant;

public record AccessToken(
        String value, Instant expiresAt
) {
}
