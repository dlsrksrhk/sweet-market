package com.sweet.market.settlement.admin;

import java.time.LocalDateTime;

import com.sweet.market.settlement.domain.SettlementStatus;

public record AdminSettlementSummaryResponse(
        Long settlementId,
        Long orderId,
        Long sellerId,
        String sellerNickname,
        Long productId,
        String productTitle,
        long amount,
        String status,
        LocalDateTime settledAt
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
            LocalDateTime settledAt
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
                settledAt
        );
    }
}
