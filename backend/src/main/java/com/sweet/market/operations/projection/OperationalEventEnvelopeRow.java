package com.sweet.market.operations.projection;

import com.sweet.market.operations.event.OperationalEvent;

public record OperationalEventEnvelopeRow(
        long outboxId,
        OperationalEvent event,
        int attemptCount
) {
}
