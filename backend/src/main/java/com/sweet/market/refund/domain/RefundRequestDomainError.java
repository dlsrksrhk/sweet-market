package com.sweet.market.refund.domain;

import com.sweet.market.common.domain.error.DomainError;

public enum RefundRequestDomainError implements DomainError {
    HANDLING_NOT_ALLOWED,
    ORDER_REQUIRED,
    BUYER_REQUIRED,
    BUYER_ORDER_MISMATCH,
    REQUEST_REASON_REQUIRED,
    REQUEST_REASON_LENGTH_INVALID,
    REJECT_REASON_REQUIRED,
    REJECT_REASON_LENGTH_INVALID
}
