package com.sweet.market.store.storefront;

import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.promotion.application.PromotionPrice;
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
        long listPrice,
        Long promotionId,
        String promotionTitle,
        long promotionDiscountAmount,
        long effectivePrice,
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
        this(id, storeId, storeName, storeType, sellerId, sellerNickname, title, price, price, null, null, 0L, price, status, thumbnailUrl,
                wishlistCount, wishlisted, carted,
                new BuyerAvailabilityResponse(salesPolicy, status, availableQuantity, lowStockThreshold));
    }

    public StorefrontProductResponse withPromotionPrice(PromotionPrice promotionPrice) {
        return new StorefrontProductResponse(
                id, storeId, storeName, storeType, sellerId, sellerNickname, title, price,
                promotionPrice.listPrice(), promotionPrice.promotionId(), promotionPrice.promotionTitle(),
                promotionPrice.promotionDiscountAmount(), promotionPrice.effectivePrice(),
                status, thumbnailUrl, wishlistCount, wishlisted, carted, availability
        );
    }
}
