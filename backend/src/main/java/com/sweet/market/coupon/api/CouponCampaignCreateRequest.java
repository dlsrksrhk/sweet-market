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
import jakarta.validation.constraints.AssertTrue;

public record CouponCampaignCreateRequest(
        @NotNull CouponScope scope, @NotNull CouponDiscountType discountType,
        @Positive long discountValue, @PositiveOrZero Long maxDiscountAmount,
        @PositiveOrZero long minimumPurchaseAmount, boolean stackable,
        @NotBlank @Size(max = 100) String title, @Size(max = 200) String label,
        @NotNull LocalDateTime issueStartsAt, @NotNull LocalDateTime issueEndsAt,
        @NotNull CouponValidityType validityType, LocalDateTime commonExpiresAt,
        @Positive Integer validityDays, @Positive Integer issueLimit, List<@Positive Long> productIds
) {
    @AssertTrue(message = "정액 할인에는 최대 할인 금액을 입력할 수 없습니다.")
    public boolean isMaximumDiscountPolicyValid() {
        return discountType == null || discountType == CouponDiscountType.PERCENTAGE || maxDiscountAmount == null;
    }

    @AssertTrue(message = "유효기간 정책을 확인해주세요.")
    public boolean isValidityPolicyValid() {
        if (validityType == null) return true;
        return validityType == CouponValidityType.COMMON_EXPIRY
                ? commonExpiresAt != null && validityDays == null
                : commonExpiresAt == null && validityDays != null;
    }

    @AssertTrue(message = "선택상품 쿠폰의 대상 상품을 확인해주세요.")
    public boolean isTargetPolicyValid() {
        if (scope == null) return true;
        List<Long> ids = productIds == null ? List.of() : productIds;
        return scope == CouponScope.ALL_PRODUCTS ? ids.isEmpty() : !ids.isEmpty() && ids.stream().distinct().count() == ids.size();
    }
}
