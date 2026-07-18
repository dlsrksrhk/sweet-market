package com.sweet.market.integration.security;

import java.util.UUID;

public record SignedHeaders(
        String apiKey,
        String keyId,
        UUID requestId,
        long timestamp,
        String signature,
        String correlationId
) {
}
