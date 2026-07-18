package com.sweet.market.integration.probe;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.sweet.market.integration.security.ExternalSystem;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProbeWebhookRequest(
        @NotNull ExternalSystem source,
        @NotBlank @Size(max = 100) String message
) {
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("Unknown webhook request field: " + name);
    }
}
