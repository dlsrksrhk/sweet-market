package com.sweet.market.promotion.api;

import com.sweet.market.promotion.domain.PromotionEffectiveStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

public record PromotionCampaignSearchRequest(
        PromotionEffectiveStatus status,
        LocalDateTime periodFrom,
        LocalDateTime periodTo,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    public int resolvedPage() {
        return page == null ? 0 : page;
    }

    public int resolvedSize() {
        return size == null ? 20 : size;
    }
}
