package com.sweet.market.operations.store;

import java.time.Instant;

public record StoreCampaignMetricResponse(
        String id,
        Instant latestBucketStart,
        String campaignKind,
        Long campaignId,
        String ownerType,
        Long ownerStoreId,
        String status,
        long claimSuccessCount,
        long claimFailureCount,
        long redemptionSuccessCount,
        long redemptionFailureCount,
        long orderSuccessCount,
        long purchaseFailureCount,
        DiscountAmountSummary promotionDiscounts,
        DiscountAmountSummary couponDiscounts
) {
}
