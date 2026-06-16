package com.sweet.market.settlement.admin;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;

import com.sweet.market.settlement.domain.SettlementStatus;

public record AdminSettlementSearchRequest(
        Long orderId,
        Long sellerId,
        SettlementStatus status,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime settledFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        LocalDateTime settledTo
) {
}
