package com.sweet.market.product.api;

import com.sweet.market.product.domain.ProductImage;

public record ProductImageResponse(
        Long id,
        String imageUrl
) {

    public static ProductImageResponse from(ProductImage image) {
        return new ProductImageResponse(image.getId(), image.getImageUrl());
    }
}
