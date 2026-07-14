package com.sweet.market.promotion.api;

import java.time.LocalDateTime;
import java.util.List;

import com.sweet.market.promotion.domain.PromotionDiscountType;
import com.sweet.market.promotion.domain.PromotionScope;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record PromotionCampaignCreateRequest(
        @NotNull PromotionScope scope,
        @NotNull PromotionDiscountType discountType,
        @PositiveOrZero long discountValue,
        int priority,
        @NotBlank @Size(max = 100) String title,
        @Size(max = 200) String label,
        @NotNull LocalDateTime startsAt,
        @NotNull LocalDateTime endsAt,
        List<@Positive Long> productIds
) {
}
