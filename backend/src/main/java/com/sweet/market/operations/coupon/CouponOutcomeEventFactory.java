package com.sweet.market.operations.coupon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventType;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class CouponOutcomeEventFactory {

    private static final String CLAIM = "CLAIM";
    private static final String REDEMPTION = "REDEMPTION";
    private static final String SUCCESS = "SUCCESS";
    private static final String FAILURE = "FAILURE";

    private final ObjectMapper objectMapper;

    public CouponOutcomeEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OperationalEvent claimSucceeded(CouponCampaign campaign, Instant occurredAt) {
        return claim(campaign, SUCCESS, CouponOutcomeReason.NONE, occurredAt);
    }

    public OperationalEvent claimFailed(
            CouponCampaign campaign,
            CouponOutcomeReason reason,
            Instant occurredAt
    ) {
        return claim(campaign, FAILURE, reason, occurredAt);
    }

    public OperationalEvent redemptionSucceeded(
            CouponCampaign campaign,
            long commerceStoreId,
            long orderId,
            long couponDiscountAmount,
            Instant occurredAt
    ) {
        return redemption(
                campaign, commerceStoreId, orderId, couponDiscountAmount,
                SUCCESS, CouponOutcomeReason.NONE, occurredAt);
    }

    public OperationalEvent redemptionFailed(
            CouponCampaign campaign,
            long commerceStoreId,
            Long orderId,
            CouponOutcomeReason reason,
            Instant occurredAt
    ) {
        return redemption(campaign, commerceStoreId, orderId, 0L, FAILURE, reason, occurredAt);
    }

    private OperationalEvent claim(
            CouponCampaign campaign,
            String result,
            CouponOutcomeReason reason,
            Instant occurredAt
    ) {
        Long ownerStoreId = ownerStoreId(campaign);
        CouponOutcomePayload payload = new CouponOutcomePayload(
                CLAIM, result, reason, campaign.getId(), campaign.getOwnerType().name(),
                ownerStoreId, ownerStoreId, null, 0L);
        return OperationalEvent.create(
                OperationalEventType.COUPON_CLAIM_OUTCOME,
                "coupon_campaign",
                campaign.getId(),
                campaign.getVersion(),
                ownerStoreId,
                campaign.getId(),
                "coupon_campaign:" + campaign.getId(),
                occurredAt,
                objectMapper.valueToTree(payload));
    }

    private OperationalEvent redemption(
            CouponCampaign campaign,
            long commerceStoreId,
            Long orderId,
            long couponDiscountAmount,
            String result,
            CouponOutcomeReason reason,
            Instant occurredAt
    ) {
        Long ownerStoreId = ownerStoreId(campaign);
        CouponOutcomePayload payload = new CouponOutcomePayload(
                REDEMPTION, result, reason, campaign.getId(), campaign.getOwnerType().name(),
                ownerStoreId, commerceStoreId, orderId, couponDiscountAmount);
        return OperationalEvent.create(
                OperationalEventType.COUPON_REDEMPTION_OUTCOME,
                orderId == null ? "coupon_campaign" : "order",
                orderId == null ? campaign.getId() : orderId,
                null,
                commerceStoreId,
                campaign.getId(),
                orderId == null ? "coupon_campaign:" + campaign.getId() : "order:" + orderId,
                occurredAt,
                objectMapper.valueToTree(payload));
    }

    private Long ownerStoreId(CouponCampaign campaign) {
        return campaign.getStore() == null ? null : campaign.getStore().getId();
    }
}
