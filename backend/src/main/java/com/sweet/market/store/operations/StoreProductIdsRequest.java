package com.sweet.market.store.operations;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record StoreProductIdsRequest(
        @NotEmpty @Size(max = 50) List<@NotNull @Positive Long> productIds
) {
}
