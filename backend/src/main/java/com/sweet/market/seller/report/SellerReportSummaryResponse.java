package com.sweet.market.seller.report;

public record SellerReportSummaryResponse(
        SellerReportTotalSummaryResponse total,
        SellerReportRecentSummaryResponse recent30Days
) {
}
