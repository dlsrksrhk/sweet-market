package com.sweet.market.operations.campaign;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventType;
import com.sweet.market.promotion.domain.PromotionCampaign;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

@Component
public class CampaignCommandEventFactory {

    private final ObjectMapper objectMapper;

    public CampaignCommandEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OperationalEvent completed(
            String campaignKind,
            long campaignId,
            Long campaignVersion,
            String ownerType,
            Long ownerStoreId,
            long actorMemberId,
            String command,
            JsonNode beforeSummary,
            JsonNode afterSummary,
            Instant occurredAt
    ) {
        CampaignCommandPayload payload = new CampaignCommandPayload(
                campaignKind, campaignId, ownerType, ownerStoreId,
                actorMemberId, command, beforeSummary, afterSummary
        );
        String aggregateType = campaignKind.toLowerCase(Locale.ROOT) + "_campaign";
        return OperationalEvent.create(
                OperationalEventType.CAMPAIGN_COMMAND_COMPLETED,
                aggregateType,
                campaignId,
                campaignVersion,
                ownerStoreId,
                campaignId,
                aggregateType + ":" + campaignId,
                occurredAt,
                objectMapper.valueToTree(payload)
        );
    }

    public JsonNode summary(PromotionCampaign campaign) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("title", campaign.getTitle());
        summary.put("label", campaign.getLabel());
        summary.put("scope", campaign.getScope().name());
        summary.put("discountType", campaign.getDiscountType().name());
        summary.put("discountValue", campaign.getDiscountValue());
        summary.put("priority", campaign.getPriority());
        summary.put("startAt", campaign.getStartAt().toString());
        summary.put("endAt", campaign.getEndAt().toString());
        summary.put("lifecycleStatus", campaign.getLifecycleStatus().name());
        ArrayNode targets = summary.putArray("targetProductIds");
        campaign.getTargets().stream()
                .map(target -> target.getProduct().getId())
                .sorted()
                .forEach(targets::add);
        return summary;
    }

    public JsonNode summary(CouponCampaign campaign) {
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("title", campaign.getTitle());
        summary.put("label", campaign.getLabel());
        summary.put("scope", campaign.getScope().name());
        summary.put("discountType", campaign.getDiscountType().name());
        summary.put("discountValue", campaign.getDiscountValue());
        summary.put("minimumPurchaseAmount", campaign.getMinimumPurchaseAmount());
        if (campaign.getIssueLimit() == null) {
            summary.putNull("issueLimit");
        } else {
            summary.put("issueLimit", campaign.getIssueLimit());
        }
        summary.put("issueStartsAt", campaign.getIssueStartsAt().toString());
        summary.put("issueEndsAt", campaign.getIssueEndsAt().toString());
        summary.put("lifecycleStatus", campaign.getLifecycleStatus().name());
        ArrayNode targets = summary.putArray("targetProductIds");
        campaign.getTargets().stream()
                .map(target -> target.getProduct().getId())
                .sorted()
                .forEach(targets::add);
        return summary;
    }
}
