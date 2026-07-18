package com.sweet.market.integration.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties("market.external-integrations")
@ConditionalOnProperty(name = "market.external-integrations.enabled", havingValue = "true")
public record ExternalIntegrationProperties(
        boolean enabled,
        @NotNull Duration allowedClockSkew,
        @Min(1_048_576) @Max(1_048_576) int maxBodyBytes,
        @NotNull Duration replayRetention,
        @Min(1) @Max(1_000) int cleanupBatchSize,
        @Valid @NotNull Inbound inbound,
        @Valid @NotNull Outbound outbound
) {

    @AssertTrue(message = "allowed clock skew must be exactly PT5M")
    public boolean isAllowedClockSkewProtocolValue() {
        return allowedClockSkew == null || Duration.ofMinutes(5).equals(allowedClockSkew);
    }

    @AssertTrue(message = "replay retention must be exactly PT10M")
    public boolean isReplayRetentionProtocolValue() {
        return replayRetention == null || Duration.ofMinutes(10).equals(replayRetention);
    }

    public InboundCredential inboundCredential(ExternalSystem source) {
        return switch (source) {
            case PAYMENT_GATEWAY -> inbound.paymentGateway();
            case DELIVERY_PROVIDER -> inbound.deliveryProvider();
        };
    }

    public OutboundCredential outboundCredential(ExternalSystem destination) {
        return switch (destination) {
            case PAYMENT_GATEWAY -> outbound.paymentGateway();
            case DELIVERY_PROVIDER -> outbound.deliveryProvider();
        };
    }

    public record Inbound(
            @Valid @NotNull InboundCredential paymentGateway,
            @Valid @NotNull InboundCredential deliveryProvider
    ) {
    }

    public record Outbound(
            @Valid @NotNull OutboundCredential paymentGateway,
            @Valid @NotNull OutboundCredential deliveryProvider
    ) {
    }

    public record InboundCredential(
            String apiKey,
            String currentKeyId,
            String currentSecret,
            String nextKeyId,
            String nextSecret
    ) {
        @AssertTrue(message = "all inbound credentials must be non-blank resolved values")
        public boolean isConfigured() {
            return usable(apiKey)
                    && usable(currentKeyId)
                    && usable(currentSecret)
                    && usable(nextKeyId)
                    && usable(nextSecret);
        }
    }

    public record OutboundCredential(
            String apiKey,
            String currentKeyId,
            String currentSecret
    ) {
        @AssertTrue(message = "all outbound credentials must be non-blank resolved values")
        public boolean isConfigured() {
            return usable(apiKey) && usable(currentKeyId) && usable(currentSecret);
        }
    }

    private static boolean usable(String value) {
        return value != null
                && !value.isBlank()
                && !value.contains("${");
    }
}
