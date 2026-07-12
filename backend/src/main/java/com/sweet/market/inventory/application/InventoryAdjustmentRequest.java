package com.sweet.market.inventory.application;

import com.sweet.market.inventory.domain.InventoryAdjustmentReason;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InventoryAdjustmentRequest(
        @NotNull @Min(0) Integer totalQuantity,
        @NotNull InventoryAdjustmentReason reason,
        @Size(max = 500) String referenceNote
) {
}
