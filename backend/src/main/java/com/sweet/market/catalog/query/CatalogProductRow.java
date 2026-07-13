package com.sweet.market.catalog.query;

import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.store.domain.StoreType;

public record CatalogProductRow(
        Long productId,
        String title,
        long price,
        ProductCategory category,
        String representativeImageUrl,
        BuyerAvailabilityResponse availability,
        ProductSalesPolicy salesPolicy,
        Long storeId,
        Long sellerId,
        String storeName,
        StoreType storeType
) {
}
