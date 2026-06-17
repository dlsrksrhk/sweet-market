package com.sweet.market.seller.report;

public record SellerReportTotalSummaryResponse(
        long activeProductCount,
        long soldOutProductCount,
        long confirmedOrderCount,
        long completedSettlementAmount,
        long unsettledConfirmedAmount
) {
}
