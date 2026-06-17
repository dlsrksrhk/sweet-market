package com.sweet.market.product.admin;

import com.sweet.market.product.domain.ProductStatus;

public record AdminProductSummaryResponse(
        Long productId,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        String status,
        String thumbnailUrl
) {

    public AdminProductSummaryResponse(
            Long productId,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl
    ) {
        this(productId, sellerId, sellerNickname, title, price, status.name(), thumbnailUrl);
    }
}
