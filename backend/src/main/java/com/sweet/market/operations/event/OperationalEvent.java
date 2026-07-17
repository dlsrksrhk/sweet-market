package com.sweet.market.operations.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record OperationalEvent(
        UUID eventId,
        OperationalEventType eventType,
        int schemaVersion,
        String aggregateType,
        Long aggregateId,
        Long aggregateVersion,
        Long storeId,
        Long campaignId,
        String partitionKey,
        Instant occurredAt,
        JsonNode payload
) {

    private static final int MAX_PAYLOAD_BYTES = 32 * 1024;

    public OperationalEvent {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType must not be blank");
        }
        if (partitionKey == null || partitionKey.isBlank()) {
            throw new IllegalArgumentException("partitionKey must not be blank");
        }
        if (payload.toString().getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload must not exceed 32 KiB after serialization");
        }
    }

    public static OperationalEvent create(
            OperationalEventType eventType, String aggregateType, Long aggregateId,
            Long aggregateVersion, Long storeId, Long campaignId,
            String partitionKey, Instant occurredAt, JsonNode payload
    ) {
        return new OperationalEvent(UUID.randomUUID(), eventType, 1, aggregateType, aggregateId,
                aggregateVersion, storeId, campaignId, partitionKey, occurredAt, payload);
    }
}
