package com.sweet.market.store.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BusinessStoreApplicationRequest(
        @NotBlank @Size(max = 100) String publicName,
        @NotBlank @Size(max = 2_000) String introduction,
        @NotBlank @Size(max = 120) String legalBusinessName,
        @NotBlank @Size(max = 40) String businessRegistrationId
) {
}
