package com.sweet.market.promotion.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sweet.market.promotion.domain.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PromotionCampaignResponse(
        Long id,
        PromotionScope scope,
        PromotionDiscountType discountType,
        long discountValue,
        int priority,
        String title,
        String label,
        String buyerText,
        String discountText,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        PromotionLifecycleStatus lifecycleStatus,
        PromotionEffectiveStatus effectiveStatus,
        int targetCount,
        List<PromotionTargetProductResponse> targets
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static PromotionCampaignResponse summary(PromotionCampaign campaign, Instant now) {
        return from(campaign, now, null);
    }

    public static PromotionCampaignResponse detail(PromotionCampaign campaign, Instant now) {
        List<PromotionTargetProductResponse> targets = campaign.getScope() == PromotionScope.SELECTED_PRODUCTS
                ? campaign.getTargets().stream().map(target -> PromotionTargetProductResponse.from(target.getProduct())).toList()
                : null;
        return from(campaign, now, targets);
    }

    private static PromotionCampaignResponse from(
            PromotionCampaign campaign,
            Instant now,
            List<PromotionTargetProductResponse> targets
    ) {
        return new PromotionCampaignResponse(
                campaign.getId(), campaign.getScope(), campaign.getDiscountType(), campaign.getDiscountValue(),
                campaign.getPriority(), campaign.getTitle(), campaign.getLabel(),
                campaign.getLabel() == null || campaign.getLabel().isBlank() ? campaign.getTitle() : campaign.getLabel(),
                discountText(campaign.getDiscountType(), campaign.getDiscountValue()),
                LocalDateTime.ofInstant(campaign.getStartAt(), KST), LocalDateTime.ofInstant(campaign.getEndAt(), KST),
                campaign.getLifecycleStatus(), campaign.effectiveStatus(now), campaign.getTargets().size(), targets
        );
    }

    private static String discountText(PromotionDiscountType discountType, long discountValue) {
        return discountType == PromotionDiscountType.FIXED_AMOUNT ? discountValue + "원 할인" : discountValue + "% 할인";
    }
}
