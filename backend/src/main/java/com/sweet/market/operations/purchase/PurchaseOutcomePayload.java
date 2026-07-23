package com.sweet.market.operations.purchase;

public record PurchaseOutcomePayload(
        String result,
        PurchaseOutcomeReason reason,
        Long orderId,
        long storeId,
        long productId,
        Long promotionCampaignId,
        Long couponCampaignId,
        long promotionDiscountAmount,
        long couponDiscountAmount
) {
}
