package com.sweet.market.delivery.api;

import com.sweet.market.delivery.domain.Delivery;

import java.time.LocalDateTime;

public record DeliveryResponse(
        Long id,
        Long orderId,
        String trackingNumber,
        String status,
        String orderStatus,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {

    public static DeliveryResponse from(Delivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getOrder().getId(),
                delivery.getTrackingNumber(),
                delivery.getStatus().name(),
                delivery.getOrder().getStatus().name(),
                delivery.getStartedAt(),
                delivery.getCompletedAt()
        );
    }
}
