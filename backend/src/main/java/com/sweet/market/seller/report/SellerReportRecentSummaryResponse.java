package com.sweet.market.seller.report;

public record SellerReportRecentSummaryResponse(
        long orderedCount,
        long confirmedOrderCount,
        long completedSettlementAmount,
        long unsettledConfirmedAmount
) {
}
