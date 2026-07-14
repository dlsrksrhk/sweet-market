package com.sweet.market.cart.api;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.promotion.application.PromotionPrice;

public record CartItemResponse(
        Long cartItemId,
        Long productId,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        long listPrice,
        Long promotionId,
        String promotionTitle,
        long promotionDiscountAmount,
        long effectivePrice,
        String status,
        String thumbnailUrl,
        LocalDateTime cartedAt,
        BuyerAvailabilityResponse availability,
        boolean checkoutAvailable,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String unavailableReason
) {

    public CartItemResponse(
            Long cartItemId,
            Long productId,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl,
            LocalDateTime cartedAt,
            ProductSalesPolicy salesPolicy,
            Integer availableQuantity,
            Integer lowStockThreshold,
            boolean checkoutAvailable,
            String unavailableReason
    ) {
        this(
                cartItemId,
                productId,
                sellerId,
                sellerNickname,
                title,
                price,
                price,
                null,
                null,
                0L,
                price,
                status.name(),
                thumbnailUrl,
                cartedAt,
                new BuyerAvailabilityResponse(salesPolicy, status, availableQuantity, lowStockThreshold),
                checkoutAvailable,
                unavailableReason
        );
    }

    public CartItemResponse withPromotionPrice(PromotionPrice promotionPrice) {
        return new CartItemResponse(
                cartItemId, productId, sellerId, sellerNickname, title, price,
                promotionPrice.listPrice(), promotionPrice.promotionId(), promotionPrice.promotionTitle(),
                promotionPrice.promotionDiscountAmount(), promotionPrice.effectivePrice(),
                status, thumbnailUrl, cartedAt, availability, checkoutAvailable, unavailableReason
        );
    }
}
