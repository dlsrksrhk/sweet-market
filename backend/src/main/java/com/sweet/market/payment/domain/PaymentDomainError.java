package com.sweet.market.payment.domain;

import com.sweet.market.common.domain.error.DomainError;

public enum PaymentDomainError implements DomainError {
    CANCELLATION_NOT_ALLOWED, REFUND_NOT_ALLOWED
}
