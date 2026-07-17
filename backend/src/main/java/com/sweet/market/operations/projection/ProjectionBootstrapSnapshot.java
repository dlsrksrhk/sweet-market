package com.sweet.market.operations.projection;

import java.time.Instant;

public record ProjectionBootstrapSnapshot(
        long generationId,
        Instant cutoff,
        long outboxHighWaterId
) {
}
