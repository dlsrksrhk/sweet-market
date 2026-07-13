package com.sweet.market.catalog.query;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catalog.cursor")
public record CatalogCursorProperties(
        String secret,
        Duration maxAge
) {

    public CatalogCursorProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("Catalog cursor secret must be provided.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("Catalog cursor secret must be at least 32 bytes.");
        }
        if (maxAge == null || maxAge.isNegative() || maxAge.isZero()) {
            throw new IllegalArgumentException("Catalog cursor max age must be positive.");
        }
    }
}
