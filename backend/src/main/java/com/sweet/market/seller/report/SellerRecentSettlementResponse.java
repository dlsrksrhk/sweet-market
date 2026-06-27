package com.sweet.market.seller.report;

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
}
