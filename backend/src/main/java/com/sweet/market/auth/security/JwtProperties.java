package com.sweet.market.auth.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String secret,
        long accessTokenValiditySeconds
) {
    public JwtProperties {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("JWT secret must be provided.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes.");
        }
        if (accessTokenValiditySeconds <= 0) {
            throw new IllegalArgumentException("JWT access token validity seconds must be positive.");
        }
    }
}
