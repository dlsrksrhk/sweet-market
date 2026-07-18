package com.sweet.market.gateway.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties("gateway.integration-security")
@Validated
public record IntegrationSecurityProperties(
        @NotNull Duration allowedClockSkew,
        @Min(1) int maxBodyBytes,
        @NotNull Duration replayRetention,
        @Min(1) int cleanupBatchSize,
        @Valid @NotEmpty List<Client> clients
) {
    public record Client(
            @NotBlank String clientId,
            @NotBlank String apiKey,
            @Valid @NotEmpty List<Key> keys
    ) {}

    public record Key(@NotBlank String keyId, @NotBlank String secret) {}
}
