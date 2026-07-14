package com.sweet.market.promotion.api;

import com.sweet.market.product.domain.Product;

public record PromotionTargetProductResponse(
        Long productId,
        String title,
        long price
) {
    public static PromotionTargetProductResponse from(Product product) {
        return new PromotionTargetProductResponse(product.getId(), product.getTitle(), product.getPrice());
    }
}
