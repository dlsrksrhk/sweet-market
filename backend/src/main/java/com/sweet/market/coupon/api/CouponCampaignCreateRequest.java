package com.sweet.market.coupon.api;

import java.time.LocalDateTime;
import java.util.List;

import com.sweet.market.coupon.domain.CouponDiscountType;
import com.sweet.market.coupon.domain.CouponScope;
import com.sweet.market.coupon.domain.CouponValidityType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CouponCampaignCreateRequest(
        @NotNull CouponScope scope, @NotNull CouponDiscountType discountType,
        @Positive long discountValue, @PositiveOrZero Long maxDiscountAmount,
        @PositiveOrZero long minimumPurchaseAmount, boolean stackable,
        @NotBlank @Size(max = 100) String title, @Size(max = 200) String label,
        @NotNull LocalDateTime issueStartsAt, @NotNull LocalDateTime issueEndsAt,
        @NotNull CouponValidityType validityType, LocalDateTime commonExpiresAt,
        @Positive Integer validityDays, List<@Positive Long> productIds
) { }
