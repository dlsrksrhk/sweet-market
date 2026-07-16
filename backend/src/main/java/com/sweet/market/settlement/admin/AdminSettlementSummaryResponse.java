package com.sweet.market.settlement.admin;

import com.sweet.market.settlement.domain.SettlementStatus;

import java.time.LocalDateTime;

public record AdminSettlementSummaryResponse(
        Long settlementId,
        Long orderId,
        Long sellerId,
        String sellerNickname,
        Long productId,
        String productTitle,
        long amount,
        String status,
        LocalDateTime settledAt,
        Long memberCouponId,
        long couponDiscountAmount
) {

    public AdminSettlementSummaryResponse(
            Long settlementId,
            Long orderId,
            Long sellerId,
            String sellerNickname,
            Long productId,
            String productTitle,
            long amount,
            SettlementStatus status,
            LocalDateTime settledAt,
            Long memberCouponId,
            long couponDiscountAmount
    ) {
        this(
                settlementId,
                orderId,
                sellerId,
                sellerNickname,
                productId,
                productTitle,
                amount,
                status.name(),
                settledAt,
                memberCouponId,
                couponDiscountAmount
        );
    }
}
