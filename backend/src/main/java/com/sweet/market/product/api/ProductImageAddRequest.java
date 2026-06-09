package com.sweet.market.product.api;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductImageAddRequest(
        @NotBlank
        @URL
        @Size(max = 500)
        String imageUrl
) {
}
