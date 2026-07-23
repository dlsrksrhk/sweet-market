package com.sweet.market.operations.inventory;

public record InventoryOutcomePayload(
        String action,
        long productId,
        long storeId,
        String salesPolicy,
        Integer availableQuantity,
        boolean soldOut
) {
}
