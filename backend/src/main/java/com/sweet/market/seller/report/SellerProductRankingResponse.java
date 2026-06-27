package com.sweet.market.seller.report;

import java.time.LocalDateTime;

public record SellerProductRankingResponse(
        Long productId,
        String title,
        String thumbnailUrl,
        long confirmedOrderCount,
        long confirmedSalesAmount,
        LocalDateTime lastConfirmedAt
) {
}
