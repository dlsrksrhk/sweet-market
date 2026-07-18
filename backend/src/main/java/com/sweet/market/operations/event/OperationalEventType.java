package com.sweet.market.operations.event;

public enum OperationalEventType {
    UNKNOWN,
    CAMPAIGN_COMMAND_COMPLETED,
    COUPON_CLAIM_OUTCOME,
    COUPON_REDEMPTION_OUTCOME,
    PURCHASE_OUTCOME,
    ORDER_STATUS_CHANGED,
    INVENTORY_OUTCOME;

    public static OperationalEventType fromStoredValue(String value) {
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
