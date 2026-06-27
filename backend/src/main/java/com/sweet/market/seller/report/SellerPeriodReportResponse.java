package com.sweet.market.seller.report;

import java.time.LocalDateTime;
import java.util.List;

public record SellerPeriodReportResponse(
        LocalDateTime generatedAt,
        SellerPeriodResponse period,
        SellerPeriodSummaryResponse summary,
        List<SellerProductRankingResponse> productRankings,
        List<SellerDailySalesResponse> dailySales,
        List<SellerRecentSaleResponse> recentSales,
        List<SellerRecentSettlementResponse> recentSettlements
) {
}
