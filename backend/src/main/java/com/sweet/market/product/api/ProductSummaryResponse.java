package com.sweet.market.product.api;

import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.store.domain.StoreType;

public record ProductSummaryResponse(
        Long id,
        Long storeId,
        String storeName,
        String storeType,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        String status,
        String thumbnailUrl,
        long wishlistCount,
        boolean wishlisted,
        boolean carted,
        BuyerAvailabilityResponse availability
) {

    public ProductSummaryResponse(
            Long id,
            Long storeId,
            String storeName,
            StoreType storeType,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl
    ) {
        this(id, storeId, storeName, storeType.name(), sellerId, sellerNickname, title, price, status.name(), thumbnailUrl, 0, false, false,
                new BuyerAvailabilityResponse(ProductSalesPolicy.SINGLE_ITEM, status, null, null));
    }

    public ProductSummaryResponse(
            Long id,
            Long storeId,
            String storeName,
            StoreType storeType,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl,
            long wishlistCount,
            boolean wishlisted,
            boolean carted
    ) {
        this(id, storeId, storeName, storeType.name(), sellerId, sellerNickname, title, price, status.name(), thumbnailUrl, wishlistCount, wishlisted, carted,
                new BuyerAvailabilityResponse(ProductSalesPolicy.SINGLE_ITEM, status, null, null));
    }

    public ProductSummaryResponse(
            Long id,
            Long storeId,
            String storeName,
            String storeType,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl
    ) {
        this(id, storeId, storeName, storeType, sellerId, sellerNickname, title, price, status.name(), thumbnailUrl, 0, false, false,
                new BuyerAvailabilityResponse(ProductSalesPolicy.SINGLE_ITEM, status, null, null));
    }

    public ProductSummaryResponse(
            Long id,
            Long storeId,
            String storeName,
            String storeType,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl,
            long wishlistCount,
            boolean wishlisted,
            boolean carted
    ) {
        this(id, storeId, storeName, storeType, sellerId, sellerNickname, title, price, status.name(), thumbnailUrl, wishlistCount, wishlisted, carted,
                new BuyerAvailabilityResponse(ProductSalesPolicy.SINGLE_ITEM, status, null, null));
    }

    public ProductSummaryResponse(
            Long id,
            Long storeId,
            String storeName,
            StoreType storeType,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl,
            long wishlistCount,
            boolean wishlisted,
            boolean carted,
            ProductSalesPolicy salesPolicy,
            Integer availableQuantity,
            Integer lowStockThreshold
    ) {
        this(id, storeId, storeName, storeType.name(), sellerId, sellerNickname, title, price, status.name(), thumbnailUrl,
                wishlistCount, wishlisted, carted,
                new BuyerAvailabilityResponse(salesPolicy, status, availableQuantity, lowStockThreshold));
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
                product.getStore().getOwnerMember().getId(),
                product.getStore().getOwnerMember().getNickname(),
                product.getTitle(),
                product.getPrice(),
                product.getStatus().name(),
                thumbnailUrl,
                wishlistCount,
                wishlisted,
                carted,
                new BuyerAvailabilityResponse(
                        product.getSalesPolicy(),
                        product.getStatus(),
                        null,
                        product.getLowStockThreshold()
                )
        );
    }
}
