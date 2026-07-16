package com.sweet.market.discovery.api;

import com.sweet.market.discovery.domain.DiscoveryEventType;
import com.sweet.market.catalog.api.CatalogProductCardResponse;

import java.time.Instant;
import java.util.List;

public record EventDetailResponse(
        DiscoveryEventType eventType,
        Long id,
        String title,
        String label,
        Long storeId,
        String storeName,
        String representativeImageUrl,
        Instant endsAt,
        List<CatalogProductCardResponse> products
) {
}
