package com.sweet.market.inventory.domain;

import com.sweet.market.common.domain.error.DomainError;

enum InventoryDomainError implements DomainError {
    SINGLE_ITEM_PRODUCT_NOT_SUPPORTED, STOCK_UNAVAILABLE, RESERVATION_NOT_FOUND,
    TOTAL_BELOW_RESERVED_QUANTITY, ADJUSTMENT_REASON_REQUIRED,
    ADJUSTMENT_ACTOR_REQUIRED, TOTAL_QUANTITY_NEGATIVE, ORDER_PRODUCT_MISMATCH
}
