package com.sweet.market.integration.security;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class HmacExternalRequestSigner implements ExternalRequestSigner {

    private final ExternalIntegrationProperties properties;
    private final HmacCanonicalizer canonicalizer;

    public HmacExternalRequestSigner(
            ExternalIntegrationProperties properties,
            HmacCanonicalizer canonicalizer
    ) {
        this.properties = properties;
        this.canonicalizer = canonicalizer;
    }

    @Override
    public SignedHeaders sign(
            ExternalSystem destination,
            String method,
            String rawTarget,
            UUID requestId,
            Instant timestamp,
            byte[] body,
            String correlationId
    ) {
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(rawTarget, "rawTarget");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(body, "body");
        requireCanonicalUuid(correlationId);

        ExternalIntegrationProperties.OutboundCredential credential =
                properties.outboundCredential(destination);
        long epochSeconds = timestamp.getEpochSecond();
        String canonical = canonicalizer.canonicalize(
                credential.currentKeyId(), epochSeconds, requestId, method, rawTarget, body);
        return new SignedHeaders(
                credential.apiKey(),
                credential.currentKeyId(),
                requestId,
                epochSeconds,
                canonicalizer.sign(credential.currentSecret(), canonical),
                correlationId
        );
    }

    private void requireCanonicalUuid(String value) {
        UUID parsed = UUID.fromString(Objects.requireNonNull(value, "correlationId"));
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("correlationId must be a canonical UUID");
        }
    }
}
