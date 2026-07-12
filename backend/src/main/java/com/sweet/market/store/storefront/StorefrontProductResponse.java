package com.sweet.market.store.storefront;

import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.store.domain.StoreType;

public record StorefrontProductResponse(
        Long id,
        Long storeId,
        String storeName,
        StoreType storeType,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        ProductStatus status,
        String thumbnailUrl,
        long wishlistCount,
        boolean wishlisted,
        boolean carted,
        BuyerAvailabilityResponse availability
) {

    public StorefrontProductResponse(
            Long id,
            Long storeId,
            String storeName,
            StoreType storeType,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl,
            long wishlistCount,
            boolean wishlisted,
            boolean carted,
            ProductSalesPolicy salesPolicy,
            Integer availableQuantity,
            Integer lowStockThreshold
    ) {
        this(id, storeId, storeName, storeType, sellerId, sellerNickname, title, price, status, thumbnailUrl,
                wishlistCount, wishlisted, carted,
                new BuyerAvailabilityResponse(salesPolicy, status, availableQuantity, lowStockThreshold));
    }
}
