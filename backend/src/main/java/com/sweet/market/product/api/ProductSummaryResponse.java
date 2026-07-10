package com.sweet.market.product.api;

import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.store.domain.StoreType;

public record ProductSummaryResponse(
        Long id,
        Long storeId,
        String storeName,
        String storeType,
        String title,
        long price,
        String status,
        String thumbnailUrl,
        long wishlistCount,
        boolean wishlisted,
        boolean carted
) {

    public ProductSummaryResponse(
            Long id,
            Long storeId,
            String storeName,
            StoreType storeType,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl
    ) {
        this(id, storeId, storeName, storeType.name(), title, price, status.name(), thumbnailUrl, 0, false, false);
    }

    public ProductSummaryResponse(
            Long id,
            Long storeId,
            String storeName,
            StoreType storeType,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl,
            long wishlistCount,
            boolean wishlisted,
            boolean carted
    ) {
        this(id, storeId, storeName, storeType.name(), title, price, status.name(), thumbnailUrl, wishlistCount, wishlisted, carted);
    }

    public ProductSummaryResponse(
            Long id,
            Long storeId,
            String storeName,
            String storeType,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl
    ) {
        this(id, storeId, storeName, storeType, title, price, status.name(), thumbnailUrl, 0, false, false);
    }

    public ProductSummaryResponse(
            Long id,
            Long storeId,
            String storeName,
            String storeType,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl,
            long wishlistCount,
            boolean wishlisted,
            boolean carted
    ) {
        this(id, storeId, storeName, storeType, title, price, status.name(), thumbnailUrl, wishlistCount, wishlisted, carted);
    }

    public static ProductSummaryResponse from(Product product) {
        return from(product, 0, false);
    }

    public static ProductSummaryResponse from(Product product, long wishlistCount, boolean wishlisted) {
        return from(product, wishlistCount, wishlisted, false);
    }

    public static ProductSummaryResponse from(
            Product product,
            long wishlistCount,
            boolean wishlisted,
            boolean carted
    ) {
        String thumbnailUrl = product.getImages().stream()
                .filter(ProductImage::isRepresentative)
                .findFirst()
                .or(() -> product.getImages().stream().findFirst())
                .map(ProductImage::getImageUrl)
                .orElse(null);

        return new ProductSummaryResponse(
                product.getId(),
                product.getStore().getId(),
                product.getStore().getPublicName(),
                product.getStore().getType().name(),
                product.getTitle(),
                product.getPrice(),
                product.getStatus().name(),
                thumbnailUrl,
                wishlistCount,
                wishlisted,
                carted
        );
    }
}
