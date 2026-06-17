package com.sweet.market.product.admin;

import com.sweet.market.product.domain.ProductStatus;

public record AdminProductSearchRequest(
        Long sellerId,
        ProductStatus status,
        String keyword
) {

    public String normalizedKeyword() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }
}
