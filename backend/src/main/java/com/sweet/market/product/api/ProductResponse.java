package com.sweet.market.product.api;

import java.util.List;

import com.sweet.market.product.domain.Product;

public record ProductResponse(
        Long id,
        Long sellerId,
        String sellerNickname,
        String title,
        String description,
        long price,
        String status,
        List<ProductImageResponse> images
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getNickname(),
                product.getTitle(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus().name(),
                product.getImages().stream()
                        .map(ProductImageResponse::from)
                        .toList()
        );
    }
}
