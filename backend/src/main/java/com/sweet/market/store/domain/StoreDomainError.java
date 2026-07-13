package com.sweet.market.store.domain;

import com.sweet.market.common.domain.error.DomainError;

public enum StoreDomainError implements DomainError {
    STATUS_TRANSITION_NOT_ALLOWED,
    REJECTION_REASON_REQUIRED,
    BUSINESS_RESUBMISSION_NOT_ALLOWED,
    BUSINESS_INFORMATION_UNAVAILABLE,
    LEGAL_INFORMATION_CHANGE_NOT_ALLOWED
}
