package com.sweet.market.operations.store;

import java.time.Instant;

public record StorePurchaseOutcomeResponse(
        String id,
        Instant latestBucketStart,
        String reason,
        long orderSuccessCount,
        long purchaseFailureCount,
        long reservationFailureCount
) {
}
