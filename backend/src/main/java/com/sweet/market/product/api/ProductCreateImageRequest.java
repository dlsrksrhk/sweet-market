package com.sweet.market.product.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ProductCreateImageRequest(
        @NotNull Long uploadId,
        @PositiveOrZero int sortOrder,
        boolean representative
) {
}
