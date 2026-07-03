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
        List<ProductImageResponse> images,
        long wishlistCount,
        boolean wishlisted,
        boolean carted
) {

    public static ProductResponse from(Product product) {
        return from(product, 0, false);
    }

    public static ProductResponse from(Product product, long wishlistCount, boolean wishlisted) {
        return from(product, wishlistCount, wishlisted, false);
    }

    public static ProductResponse from(Product product, long wishlistCount, boolean wishlisted, boolean carted) {
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
                        .toList(),
                wishlistCount,
                wishlisted,
                carted
        );
    }
}
