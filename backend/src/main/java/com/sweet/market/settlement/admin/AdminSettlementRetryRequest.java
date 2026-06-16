package com.sweet.market.settlement.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AdminSettlementRetryRequest(
        @NotNull @Positive Long orderId
) {
}
