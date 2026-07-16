package com.sweet.market.order.admin;

import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.product.domain.ProductStatus;

import java.time.LocalDateTime;

public record AdminOrderSummaryResponse(
        Long orderId,
        Long productId,
        String productTitle,
        long productPrice,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        String status,
        String productStatus,
        LocalDateTime orderedAt,
        Long memberCouponId,
        long couponDiscountAmount
) {

    public AdminOrderSummaryResponse(
            Long orderId,
            Long productId,
            String productTitle,
            long productPrice,
            Long buyerId,
            String buyerNickname,
            Long sellerId,
            String sellerNickname,
            OrderStatus status,
            ProductStatus productStatus,
            LocalDateTime orderedAt,
            Long memberCouponId,
            long couponDiscountAmount
    ) {
        this(
                orderId,
                productId,
                productTitle,
                productPrice,
                buyerId,
                buyerNickname,
                sellerId,
                sellerNickname,
                status.name(),
                productStatus.name(),
                orderedAt,
                memberCouponId,
                couponDiscountAmount
        );
    }
}
