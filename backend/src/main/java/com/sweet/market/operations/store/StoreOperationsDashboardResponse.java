package com.sweet.market.operations.store;

import com.sweet.market.operations.api.DashboardPeriod;

import java.time.Instant;
import java.util.List;

public record StoreOperationsDashboardResponse(
        Long storeId,
        String storeName,
        DashboardPeriod period,
        Instant generatedAt,
        Instant projectionUpdatedAt,
        long projectionLagSeconds,
        Instant trackingStartedAt,
        long claimSuccessCount,
        long redemptionSuccessCount,
        long orderSuccessCount,
        long purchaseFailureCount,
        DiscountAmountSummary promotionDiscounts,
        DiscountAmountSummary couponDiscounts,
        long lowStockCount,
        long soldOutTransitionCount,
        List<OutcomeReasonCount> leadingFailureReasons
) {
}
