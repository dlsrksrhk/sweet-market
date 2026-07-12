package com.sweet.market.inventory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;

public record BuyerAvailabilityResponse(
        ProductSalesPolicy policy,
        AvailabilityStatus status,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Integer quantity
) {

    public BuyerAvailabilityResponse(
            ProductSalesPolicy policy,
            ProductStatus catalogStatus,
            Integer availableQuantity,
            Integer lowStockThreshold
    ) {
        this(policy, availabilityStatus(policy, catalogStatus, availableQuantity, lowStockThreshold),
                visibleQuantity(policy, catalogStatus, availableQuantity, lowStockThreshold));
    }

    public enum AvailabilityStatus {
        IN_STOCK,
        LOW_STOCK,
        SOLD_OUT
    }

    private static AvailabilityStatus availabilityStatus(
            ProductSalesPolicy policy,
            ProductStatus catalogStatus,
            Integer availableQuantity,
            Integer lowStockThreshold
    ) {
        if (policy == ProductSalesPolicy.STOCK_MANAGED) {
            if (availableQuantity == null || availableQuantity <= 0) {
                return AvailabilityStatus.SOLD_OUT;
            }
            if (lowStockThreshold != null && availableQuantity <= lowStockThreshold) {
                return AvailabilityStatus.LOW_STOCK;
            }
            return AvailabilityStatus.IN_STOCK;
        }
        return catalogStatus == ProductStatus.ON_SALE
                ? AvailabilityStatus.IN_STOCK
                : AvailabilityStatus.SOLD_OUT;
    }

    private static Integer visibleQuantity(
            ProductSalesPolicy policy,
            ProductStatus catalogStatus,
            Integer availableQuantity,
            Integer lowStockThreshold
    ) {
        return availabilityStatus(policy, catalogStatus, availableQuantity, lowStockThreshold)
                == AvailabilityStatus.LOW_STOCK ? availableQuantity : null;
    }
}
