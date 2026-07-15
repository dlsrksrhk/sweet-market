package com.sweet.market.coupon.api;

import com.sweet.market.product.domain.Product;

public record CouponTargetProductResponse(Long productId, String title, long price) {
    public static CouponTargetProductResponse from(Product product) {
        return new CouponTargetProductResponse(product.getId(), product.getTitle(), product.getPrice());
    }
}
