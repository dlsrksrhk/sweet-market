package com.sweet.market.order.application;

import java.time.LocalDateTime;

public record OrderAutoConfirmResult(
        int confirmedCount,
        LocalDateTime deliveredBefore,
        int thresholdDays,
        LocalDateTime executedAt
) {
}
