package com.sweet.market.operations.store;

import java.time.Instant;

public record StoreInventoryPressureResponse(
        Long productId,
        String salesPolicy,
        Integer availableQuantity,
        boolean lowStock,
        Instant lastSoldOutAt,
        long reservationFailureCount,
        Instant lastReservationFailureAt,
        Instant updatedAt
) {
}
