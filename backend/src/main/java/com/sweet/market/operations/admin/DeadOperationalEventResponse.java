package com.sweet.market.operations.admin;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record DeadOperationalEventResponse(
        long id,
        UUID eventId,
        String eventType,
        int schemaVersion,
        String aggregateType,
        Long aggregateId,
        Long aggregateVersion,
        Long storeId,
        Long campaignId,
        String partitionKey,
        Instant occurredAt,
        JsonNode payload,
        String deliveryState,
        int attemptCount,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt
) {
}
