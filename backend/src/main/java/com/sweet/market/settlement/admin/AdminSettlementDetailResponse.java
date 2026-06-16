package com.sweet.market.settlement.admin;

import java.time.LocalDateTime;

import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.settlement.domain.SettlementStatus;

public record AdminSettlementDetailResponse(
        Long settlementId,
        Long orderId,
        String orderStatus,
        LocalDateTime confirmedAt,
        Long buyerId,
        String buyerNickname,
        Long sellerId,
        String sellerNickname,
        Long productId,
        String productTitle,
        long amount,
        String status,
        LocalDateTime settledAt
) {

    public AdminSettlementDetailResponse(
            Long settlementId,
            Long orderId,
            OrderStatus orderStatus,
            LocalDateTime confirmedAt,
            Long buyerId,
            String buyerNickname,
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
                orderStatus.name(),
                confirmedAt,
                buyerId,
                buyerNickname,
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
