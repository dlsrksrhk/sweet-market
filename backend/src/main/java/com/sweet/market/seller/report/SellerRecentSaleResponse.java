package com.sweet.market.seller.report;

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
}
