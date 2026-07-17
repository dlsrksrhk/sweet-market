package com.sweet.market.operations.store;

import java.time.Instant;
import java.util.UUID;

public record StoreCampaignAuditResponse(
        Long id,
        UUID eventId,
        String campaignKind,
        Long campaignId,
        String ownerType,
        Long ownerStoreId,
        Long actorMemberId,
        String command,
        Instant occurredAt,
        Long aggregateVersion,
        String beforeSummary,
        String afterSummary
) {
}
