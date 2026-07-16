package com.sweet.market.cart.repository;

import com.sweet.market.cart.api.CartItemResponse;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;

import java.time.LocalDateTime;

public record CartItemReadRow(
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

    public CartItemResponse toResponse() {
        return new CartItemResponse(
                cartItemId,
                productId,
                sellerId,
                sellerNickname,
                title,
                price,
                status,
                thumbnailUrl,
                cartedAt,
                salesPolicy,
                availableQuantity,
                lowStockThreshold,
                checkoutAvailable,
                unavailableReason
        );
    }
}
