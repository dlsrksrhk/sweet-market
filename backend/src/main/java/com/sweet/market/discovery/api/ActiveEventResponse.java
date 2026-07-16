package com.sweet.market.discovery.api;

import com.sweet.market.discovery.domain.DiscoveryEventType;

import java.time.Instant;

public record ActiveEventResponse(
        DiscoveryEventType eventType,
        Long eventId,
        String title,
        String label,
        Long storeId,
        String storeName,
        String representativeImageUrl,
        Instant endsAt
) {
}
