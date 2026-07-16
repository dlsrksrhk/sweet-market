package com.sweet.market.seller.report;

import com.sweet.market.settlement.domain.SettlementStatus;

import java.time.LocalDateTime;

public record SellerRecentSettlementResponse(
        Long settlementId,
        Long orderId,
        Long productId,
        String productTitle,
        long amount,
        String status,
        LocalDateTime settledAt
) {

    public SellerRecentSettlementResponse(
            Long settlementId,
            Long orderId,
            Long productId,
            String productTitle,
            long amount,
            SettlementStatus status,
            LocalDateTime settledAt
    ) {
        this(settlementId, orderId, productId, productTitle, amount, status.name(), settledAt);
    }
}
