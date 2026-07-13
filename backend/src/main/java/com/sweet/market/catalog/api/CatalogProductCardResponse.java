package com.sweet.market.catalog.api;

import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.store.domain.StoreType;

public record CatalogProductCardResponse(
        Long id,
        String title,
        long price,
        ProductCategory category,
        String representativeImageUrl,
        BuyerAvailabilityResponse availability,
        ProductSalesPolicy salesPolicy,
        Long storeId,
        String storeName,
        StoreType storeType,
        boolean wishlisted,
        boolean carted
) {
}
