package com.sweet.market.operations.campaign;

import com.fasterxml.jackson.databind.JsonNode;

public record CampaignCommandPayload(
        String campaignKind,
        long campaignId,
        String ownerType,
        Long ownerStoreId,
        long actorMemberId,
        String command,
        JsonNode beforeSummary,
        JsonNode afterSummary
) {
}
