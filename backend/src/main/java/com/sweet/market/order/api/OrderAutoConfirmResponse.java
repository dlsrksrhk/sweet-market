package com.sweet.market.order.api;

import com.sweet.market.order.application.OrderAutoConfirmResult;

import java.time.LocalDateTime;

public record OrderAutoConfirmResponse(
        int confirmedCount,
        LocalDateTime deliveredBefore,
        int thresholdDays,
        LocalDateTime executedAt
) {

    public static OrderAutoConfirmResponse from(OrderAutoConfirmResult result) {
        return new OrderAutoConfirmResponse(
                result.confirmedCount(),
                result.deliveredBefore(),
                result.thresholdDays(),
                result.executedAt()
        );
    }
}
