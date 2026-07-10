package com.sweet.market.store.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StoreProfileUpdateRequest(
        @NotBlank @Size(max = 100) String publicName,
        @NotBlank @Size(max = 2_000) String introduction,
        @Size(max = 120) String legalBusinessName,
        @Size(max = 40) String businessRegistrationId
) {

    public boolean includesLegalBusinessInformation() {
        return legalBusinessName != null || businessRegistrationId != null;
    }
}
