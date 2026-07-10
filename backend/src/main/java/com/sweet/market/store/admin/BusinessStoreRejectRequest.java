package com.sweet.market.store.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessStoreRejectRequest(@NotBlank @Size(max = 1_000) String reason) {
}
