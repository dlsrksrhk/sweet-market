package com.sweet.market.coupon.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AvailableCouponCampaignSearchRequest(
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    public int resolvedPage() { return page == null ? 0 : page; }
    public int resolvedSize() { return size == null ? 20 : size; }
}
