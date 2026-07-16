package com.sweet.market.catalog.query;

import com.sweet.market.catalog.domain.CatalogSort;

import java.time.Instant;
import java.util.Objects;

public record CatalogCursor(
        CatalogSort sort,
        Long price,
        Long productId,
        String filterFingerprint,
        Instant expiresAt
) {

    public CatalogCursor {
        Objects.requireNonNull(sort, "sort must not be null");
        Objects.requireNonNull(productId, "productId must not be null");
        if (filterFingerprint == null || filterFingerprint.isBlank()) {
            throw new IllegalArgumentException("filterFingerprint must not be blank");
        }
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }
}
