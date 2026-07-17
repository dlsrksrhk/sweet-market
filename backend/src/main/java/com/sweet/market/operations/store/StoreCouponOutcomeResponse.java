package com.sweet.market.operations.store;

import java.time.Instant;

public record StoreCouponOutcomeResponse(
        String id,
        Instant latestBucketStart,
        Long campaignId,
        String ownerType,
        Long ownerStoreId,
        String reason,
        long claimSuccessCount,
        long claimFailureCount,
        long redemptionSuccessCount,
        long redemptionFailureCount,
        DiscountAmountSummary discounts
) {
}
