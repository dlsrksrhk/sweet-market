package com.sweet.market.seller.report;

public record SellerPeriodSummaryResponse(
        long orderedCount,
        long confirmedOrderCount,
        long confirmedSalesAmount,
        long completedSettlementAmount,
        long unsettledConfirmedAmount,
        long averageConfirmedOrderAmount
) {
}
