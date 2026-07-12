package com.sweet.market.store.operations;

import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;

public record StoreCatalogProductResponse(
        Long productId,
        String thumbnailUrl,
        String title,
        long price,
        ProductStatus status,
        ProductSalesPolicy salesPolicy,
        Integer totalQuantity,
        Integer reservedQuantity,
        Integer availableQuantity,
        Integer lowStockThreshold
) {
}
