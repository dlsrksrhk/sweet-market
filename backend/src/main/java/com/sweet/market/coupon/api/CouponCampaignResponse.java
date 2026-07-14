package com.sweet.market.coupon.api;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sweet.market.coupon.domain.CouponCampaign;
import com.sweet.market.coupon.domain.CouponCampaignOwnerType;
import com.sweet.market.coupon.domain.CouponDiscountType;
import com.sweet.market.coupon.domain.CouponEffectiveStatus;
import com.sweet.market.coupon.domain.CouponLifecycleStatus;
import com.sweet.market.coupon.domain.CouponScope;
import com.sweet.market.coupon.domain.CouponValidityType;
import com.sweet.market.store.domain.Store;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CouponCampaignResponse(
        Long id, CouponCampaignOwnerType ownerType, StoreSummary store, CouponScope scope,
        CouponDiscountType discountType, long discountValue, Long maxDiscountAmount, long minimumPurchaseAmount,
        boolean stackable, String title, String label, LocalDateTime issueStartsAt, LocalDateTime issueEndsAt,
        CouponValidityType validityType, LocalDateTime commonExpiresAt, Integer validityDays,
        CouponLifecycleStatus lifecycleStatus, CouponEffectiveStatus effectiveStatus, int targetCount,
        List<CouponTargetProductResponse> targets
) {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static CouponCampaignResponse summary(CouponCampaign campaign, Instant now) { return from(campaign, now, null); }
    public static CouponCampaignResponse detail(CouponCampaign campaign, Instant now) {
        List<CouponTargetProductResponse> targets = campaign.getScope() == CouponScope.SELECTED_PRODUCTS
                ? campaign.getTargets().stream().map(target -> CouponTargetProductResponse.from(target.getProduct())).toList() : null;
        return from(campaign, now, targets);
    }
    private static CouponCampaignResponse from(CouponCampaign campaign, Instant now, List<CouponTargetProductResponse> targets) {
        return new CouponCampaignResponse(campaign.getId(), campaign.getOwnerType(), StoreSummary.from(campaign.getStore()),
                campaign.getScope(), campaign.getDiscountType(), campaign.getDiscountValue(), campaign.getMaxDiscountAmount(),
                campaign.getMinimumPurchaseAmount(), campaign.isStackable(), campaign.getTitle(), campaign.getLabel(),
                local(campaign.getIssueStartsAt()), local(campaign.getIssueEndsAt()), campaign.getValidityType(),
                local(campaign.getCommonExpiresAt()), campaign.getValidityDays(), campaign.getLifecycleStatus(),
                campaign.effectiveStatus(now), campaign.getTargets().size(), targets);
    }
    private static LocalDateTime local(Instant instant) { return instant == null ? null : LocalDateTime.ofInstant(instant, KST); }
    public record StoreSummary(Long id, String publicName) { static StoreSummary from(Store store) { return store == null ? null : new StoreSummary(store.getId(), store.getPublicName()); } }
}
