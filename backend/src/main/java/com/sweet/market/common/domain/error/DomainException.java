package com.sweet.market.common.domain.error;

public final class DomainException extends RuntimeException {

    private final DomainError error;

    public DomainException(DomainError error) {
        super(error.toString());
        this.error = error;
    }

    public DomainError error() {
        return error;
    }
}
