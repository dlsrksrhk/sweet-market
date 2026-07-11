package com.sweet.market.store.operations;

import com.sweet.market.product.domain.ProductStatus;

public record StoreCatalogProductResponse(
        Long productId,
        String thumbnailUrl,
        String title,
        long price,
        ProductStatus status
) {
}
