package com.sweet.market.product.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @Positive
        long price
) {
}
