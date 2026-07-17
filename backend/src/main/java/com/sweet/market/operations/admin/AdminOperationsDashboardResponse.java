package com.sweet.market.operations.admin;

import com.sweet.market.operations.api.DashboardPeriod;
import com.sweet.market.operations.projection.ProjectionHealthResponse;
import com.sweet.market.operations.store.DiscountAmountSummary;
import com.sweet.market.operations.store.OutcomeReasonCount;

import java.time.Instant;
import java.util.List;

public record AdminOperationsDashboardResponse(
        Long storeId,
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
        long auditCount,
        List<OutcomeReasonCount> leadingFailureReasons,
        ProjectionHealthResponse health
) {

    public record OutcomeResponse(
            String id,
            String outcomeType,
            Instant latestBucketStart,
            Long storeId,
            String campaignKind,
            Long campaignId,
            String ownerType,
            Long ownerStoreId,
            Long productId,
            String reason,
            long successCount,
            long failureCount,
            long reservationFailureCount
    ) {
    }
}
