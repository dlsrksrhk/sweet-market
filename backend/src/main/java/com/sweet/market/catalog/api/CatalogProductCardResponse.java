package com.sweet.market.catalog.api;

import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.store.domain.StoreType;

public record CatalogProductCardResponse(
        Long id,
        String title,
        long price,
        long listPrice,
        Long promotionId,
        String promotionTitle,
        long promotionDiscountAmount,
        long effectivePrice,
        ProductCategory category,
        String representativeImageUrl,
        BuyerAvailabilityResponse availability,
        ProductSalesPolicy salesPolicy,
        Long storeId,
        Long sellerId,
        String storeName,
        StoreType storeType,
        boolean wishlisted,
        boolean carted
) {
}
