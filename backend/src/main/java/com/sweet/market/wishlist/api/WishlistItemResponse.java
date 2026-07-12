package com.sweet.market.wishlist.api;

import java.time.LocalDateTime;

import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.inventory.api.BuyerAvailabilityResponse;

public record WishlistItemResponse(
        Long wishlistItemId,
        Long productId,
        Long sellerId,
        String sellerNickname,
        String title,
        long price,
        String status,
        String thumbnailUrl,
        boolean wishlisted,
        long wishlistCount,
        BuyerAvailabilityResponse availability,
        LocalDateTime wishedAt
) {

    public WishlistItemResponse(
            Long wishlistItemId,
            Long productId,
            Long sellerId,
            String sellerNickname,
            String title,
            long price,
            ProductStatus status,
            String thumbnailUrl,
            boolean wishlisted,
            long wishlistCount,
            ProductSalesPolicy salesPolicy,
            Integer availableQuantity,
            Integer lowStockThreshold,
            LocalDateTime wishedAt
    ) {
        this(
                wishlistItemId,
                productId,
                sellerId,
                sellerNickname,
                title,
                price,
                catalogStatus(salesPolicy, status, availableQuantity).name(),
                thumbnailUrl,
                wishlisted,
                wishlistCount,
                new BuyerAvailabilityResponse(
                        salesPolicy,
                        catalogStatus(salesPolicy, status, availableQuantity),
                        availableQuantity,
                        lowStockThreshold
                ),
                wishedAt
        );
    }

    private static ProductStatus catalogStatus(
            ProductSalesPolicy salesPolicy,
            ProductStatus status,
            Integer availableQuantity
    ) {
        if (salesPolicy != ProductSalesPolicy.STOCK_MANAGED) {
            return status;
        }
        return availableQuantity != null && availableQuantity > 0
                ? ProductStatus.ON_SALE
                : ProductStatus.SOLD_OUT;
    }
}
