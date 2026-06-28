package com.sweet.market.product.api;

import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.product.domain.ProductStatus;

public record ProductSummaryResponse(
        Long id,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        String status,
        String thumbnailUrl,
        long wishlistCount,
        boolean wishlisted
) {

    public ProductSummaryResponse(
            Long id,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl
    ) {
        this(id, sellerId, sellerNickname, title, price, status.name(), thumbnailUrl, 0, false);
    }

    public ProductSummaryResponse(
            Long id,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl,
            long wishlistCount,
            boolean wishlisted
    ) {
        this(id, sellerId, sellerNickname, title, price, status.name(), thumbnailUrl, wishlistCount, wishlisted);
    }

    public static ProductSummaryResponse from(Product product) {
        return from(product, 0, false);
    }

    public static ProductSummaryResponse from(Product product, long wishlistCount, boolean wishlisted) {
        String thumbnailUrl = product.getImages().stream()
                .filter(ProductImage::isRepresentative)
                .findFirst()
                .or(() -> product.getImages().stream().findFirst())
                .map(ProductImage::getImageUrl)
                .orElse(null);

        return new ProductSummaryResponse(
                product.getId(),
                product.getSeller().getId(),
                product.getSeller().getNickname(),
                product.getTitle(),
                product.getPrice(),
                product.getStatus().name(),
                thumbnailUrl,
                wishlistCount,
                wishlisted
        );
    }
}
