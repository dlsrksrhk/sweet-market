package com.sweet.market.cart.api;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;

public record CartItemResponse(
        Long cartItemId,
        Long productId,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
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
                status.name(),
                thumbnailUrl,
                cartedAt,
                new BuyerAvailabilityResponse(salesPolicy, status, availableQuantity, lowStockThreshold),
                checkoutAvailable,
                unavailableReason
        );
    }
}
