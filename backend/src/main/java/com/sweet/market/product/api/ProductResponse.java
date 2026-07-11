package com.sweet.market.product.api;

import java.util.List;

import com.sweet.market.product.domain.Product;

public record ProductResponse(
        Long id,
        Long storeId,
        String storeName,
        String storeType,
        Long sellerId,
        String sellerNickname,
        String title,
        String description,
        long price,
        String status,
        boolean purchasable,
        List<ProductImageResponse> images,
        long wishlistCount,
        boolean wishlisted,
        boolean carted,
        long reviewCount,
        Double averageRating,
        long sellerReviewCount,
        Double sellerAverageRating
) {

    public static ProductResponse from(Product product) {
        return from(product, 0, false);
    }

    public static ProductResponse from(Product product, long wishlistCount, boolean wishlisted) {
        return from(product, wishlistCount, wishlisted, false);
    }

    public static ProductResponse from(Product product, long wishlistCount, boolean wishlisted, boolean carted) {
        return from(product, wishlistCount, wishlisted, carted, 0, null, 0, null);
    }

    public static ProductResponse from(
            Product product,
            long wishlistCount,
            boolean wishlisted,
            boolean carted,
            long reviewCount,
            Double averageRating,
            long sellerReviewCount,
            Double sellerAverageRating
    ) {
        return new ProductResponse(
                product.getId(),
                product.getStore().getId(),
                product.getStore().getPublicName(),
                product.getStore().getType().name(),
                product.getStore().getOwnerMember().getId(),
                product.getStore().getOwnerMember().getNickname(),
                product.getTitle(),
                product.getDescription(),
                product.getPrice(),
                product.getStatus().name(),
                product.isPurchasable(),
                product.getImages().stream()
                        .map(ProductImageResponse::from)
                        .toList(),
                wishlistCount,
                wishlisted,
                carted,
                reviewCount,
                averageRating,
                sellerReviewCount,
                sellerAverageRating
        );
    }
}
