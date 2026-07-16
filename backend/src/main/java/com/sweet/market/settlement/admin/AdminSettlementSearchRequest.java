package com.sweet.market.settlement.admin;

import com.sweet.market.settlement.domain.SettlementStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

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
