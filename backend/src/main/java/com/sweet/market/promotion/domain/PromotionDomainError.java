package com.sweet.market.promotion.domain;

import com.sweet.market.common.domain.error.DomainError;

public enum PromotionDomainError implements DomainError {
    INVALID_PERIOD,
    INVALID_DISCOUNT_VALUE,
    SELECTED_TARGET_REQUIRED,
    STORE_WIDE_TARGET_NOT_ALLOWED,
    DUPLICATE_TARGET,
    LIFECYCLE_TRANSITION_NOT_ALLOWED,
    UPDATE_NOT_ALLOWED
}
