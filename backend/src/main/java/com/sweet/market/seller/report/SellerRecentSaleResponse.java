package com.sweet.market.seller.report;

import com.sweet.market.settlement.domain.SettlementStatus;

import java.time.LocalDateTime;

public record SellerRecentSaleResponse(
        Long orderId,
        Long productId,
        String productTitle,
        String buyerNickname,
        long amount,
        LocalDateTime confirmedAt,
        String settlementStatus
) {

    public SellerRecentSaleResponse(
            Long orderId,
            Long productId,
            String productTitle,
            String buyerNickname,
            long amount,
            LocalDateTime confirmedAt,
            SettlementStatus settlementStatus
    ) {
        this(
                orderId,
                productId,
                productTitle,
                buyerNickname,
                amount,
                confirmedAt,
                settlementStatus == null ? "NONE" : settlementStatus.name()
        );
    }
}
