package com.sweet.market.wishlist.api;

import java.time.LocalDateTime;

import com.sweet.market.product.domain.ProductStatus;

public record WishlistItemResponse(
        Long wishlistItemId,
        Long productId,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        String status,
        String thumbnailUrl,
        boolean wishlisted,
        long wishlistCount,
        LocalDateTime wishedAt
) {

    public WishlistItemResponse(
            Long wishlistItemId,
            Long productId,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl,
            boolean wishlisted,
            long wishlistCount,
            LocalDateTime wishedAt
    ) {
        this(
                wishlistItemId,
                productId,
                sellerId,
                sellerNickname,
                title,
                price,
                status.name(),
                thumbnailUrl,
                wishlisted,
                wishlistCount,
                wishedAt
        );
    }
}
