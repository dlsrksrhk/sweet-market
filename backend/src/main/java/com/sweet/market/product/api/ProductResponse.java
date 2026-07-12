package com.sweet.market.product.api;

import java.util.List;

import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;

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
        BuyerAvailabilityResponse availability,
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

    public static ProductResponse from(
            Product product,
            long wishlistCount,
            boolean wishlisted,
            BuyerAvailabilityResponse availability
    ) {
        return from(product, wishlistCount, wishlisted, false, availability, 0, null, 0, null);
    }

    public static ProductResponse from(Product product, long wishlistCount, boolean wishlisted, boolean carted) {
        return from(product, wishlistCount, wishlisted, carted, defaultAvailability(product), 0, null, 0, null);
    }

    public static ProductResponse from(
            Product product,
            long wishlistCount,
            boolean wishlisted,
            boolean carted,
            BuyerAvailabilityResponse availability,
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
                catalogStatus(product, availability).name(),
                product.isPurchasable()
                        && availability.status() != BuyerAvailabilityResponse.AvailabilityStatus.SOLD_OUT,
                product.getImages().stream()
                        .map(ProductImageResponse::from)
                        .toList(),
                wishlistCount,
                wishlisted,
                carted,
                availability,
                reviewCount,
                averageRating,
                sellerReviewCount,
                sellerAverageRating
        );
    }

    private static BuyerAvailabilityResponse defaultAvailability(Product product) {
        return new BuyerAvailabilityResponse(product.getSalesPolicy(), product.getStatus(), null, product.getLowStockThreshold());
    }

    private static ProductStatus catalogStatus(Product product, BuyerAvailabilityResponse availability) {
        if (product.getSalesPolicy() == ProductSalesPolicy.SINGLE_ITEM) {
            return product.getStatus();
        }
        if (product.getStatus() == ProductStatus.HIDDEN) {
            return ProductStatus.HIDDEN;
        }
        return availability.status() == BuyerAvailabilityResponse.AvailabilityStatus.SOLD_OUT
                ? ProductStatus.SOLD_OUT
                : ProductStatus.ON_SALE;
    }
}
