package com.sweet.market.operations.purchase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweet.market.operations.event.OperationalEvent;
import com.sweet.market.operations.event.OperationalEventType;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PurchaseOutcomeEventFactory {

    private final ObjectMapper objectMapper;

    public PurchaseOutcomeEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public OperationalEvent orderCreated(
            long orderId,
            long storeId,
            long productId,
            Long promotionCampaignId,
            Long couponCampaignId,
            long promotionDiscountAmount,
            long couponDiscountAmount,
            Instant occurredAt
    ) {
        return event(OperationalEventType.PURCHASE_OUTCOME, "SUCCESS", PurchaseOutcomeReason.NONE,
                orderId, storeId, productId, promotionCampaignId, couponCampaignId,
                promotionDiscountAmount, couponDiscountAmount, occurredAt);
    }

    public OperationalEvent purchaseFailed(
            long storeId,
            long productId,
            PurchaseOutcomeReason reason,
            Instant occurredAt
    ) {
        return event(OperationalEventType.PURCHASE_OUTCOME, "FAILURE", reason,
                null, storeId, productId, null, null, 0L, 0L, occurredAt);
    }

    public OperationalEvent paymentFailed(
            long orderId,
            long storeId,
            long productId,
            Long promotionCampaignId,
            Long couponCampaignId,
            long promotionDiscountAmount,
            long couponDiscountAmount,
            Instant occurredAt
    ) {
        return event(OperationalEventType.PURCHASE_OUTCOME, "FAILURE", PurchaseOutcomeReason.PAYMENT_FAILED,
                orderId, storeId, productId, promotionCampaignId, couponCampaignId,
                promotionDiscountAmount, couponDiscountAmount, occurredAt);
    }

    public OperationalEvent orderStatusChanged(
            String status,
            long orderId,
            long storeId,
            long productId,
            Long promotionCampaignId,
            Long couponCampaignId,
            long promotionDiscountAmount,
            long couponDiscountAmount,
            Instant occurredAt
    ) {
        return event(OperationalEventType.ORDER_STATUS_CHANGED, status, PurchaseOutcomeReason.NONE,
                orderId, storeId, productId, promotionCampaignId, couponCampaignId,
                promotionDiscountAmount, couponDiscountAmount, occurredAt);
    }

    private OperationalEvent event(
            OperationalEventType eventType,
            String result,
            PurchaseOutcomeReason reason,
            Long orderId,
            long storeId,
            long productId,
            Long promotionCampaignId,
            Long couponCampaignId,
            long promotionDiscountAmount,
            long couponDiscountAmount,
            Instant occurredAt
    ) {
        PurchaseOutcomePayload payload = new PurchaseOutcomePayload(
                result, reason, orderId, storeId, productId, promotionCampaignId, couponCampaignId,
                promotionDiscountAmount, couponDiscountAmount);
        return OperationalEvent.create(
                eventType,
                orderId == null ? "product" : "order",
                orderId == null ? productId : orderId,
                null,
                storeId,
                null,
                orderId == null ? "product:" + productId : "order:" + orderId,
                occurredAt,
                objectMapper.valueToTree(payload));
    }
}
