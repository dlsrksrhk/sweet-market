package com.sweet.market.payment.api;

import java.time.LocalDateTime;

import com.sweet.market.payment.domain.Payment;

public record PaymentResponse(
        Long id,
        Long orderId,
        String externalPaymentId,
        String status,
        String orderStatus,
        LocalDateTime approvedAt,
        LocalDateTime canceledAt
) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getExternalPaymentId(),
                payment.getStatus().name(),
                payment.getOrder().getStatus().name(),
                payment.getApprovedAt(),
                payment.getCanceledAt()
        );
    }
}
