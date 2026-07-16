package com.sweet.market.store.operations;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StoreProductIdsRequest(
        @NotEmpty @Size(max = 50) List<@NotNull @Positive Long> productIds
) {
}
