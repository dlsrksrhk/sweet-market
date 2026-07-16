package com.sweet.market.promotion.api;

import com.sweet.market.promotion.domain.PromotionDiscountType;
import com.sweet.market.promotion.domain.PromotionScope;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public record PromotionCampaignUpdateRequest(
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
