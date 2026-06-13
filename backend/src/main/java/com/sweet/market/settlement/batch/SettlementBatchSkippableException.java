package com.sweet.market.settlement.batch;

public class SettlementBatchSkippableException extends RuntimeException {

    public SettlementBatchSkippableException(String message) {
        super(message);
    }

    public SettlementBatchSkippableException(String message, Throwable cause) {
        super(message, cause);
    }
}
