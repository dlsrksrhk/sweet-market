package com.sweet.market.settlement.api;

import java.time.LocalDateTime;

import com.sweet.market.settlement.domain.Settlement;

public record SettlementResponse(
        Long id,
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

    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getOrder().getId(),
                settlement.getSeller().getId(),
                settlement.getSeller().getNickname(),
                settlement.getOrder().getProduct().getId(),
                settlement.getOrder().getProduct().getTitle(),
                settlement.getAmount(),
                settlement.getStatus().name(),
                settlement.getSettledAt(),
                settlement.getOrder().getMemberCouponId(),
                settlement.getOrder().getCouponDiscountAmount()
        );
    }
}
