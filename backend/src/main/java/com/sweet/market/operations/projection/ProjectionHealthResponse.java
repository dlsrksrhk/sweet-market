package com.sweet.market.operations.projection;

import java.time.Instant;

public record ProjectionHealthResponse(
        long pendingCount,
        long retryCount,
        long deadCount,
        Instant oldestUnprocessedAt,
        long projectionLagSeconds,
        Instant projectionUpdatedAt
) {
}
