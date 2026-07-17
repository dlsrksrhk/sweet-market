package com.sweet.market.operations.projection;

import java.time.Instant;

public record ProjectionRebuildResult(
        long generationId,
        String status,
        Instant cutoff,
        Instant activatedAt
) {
}
