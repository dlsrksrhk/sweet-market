package com.sweet.market.provider.security;

import java.time.Instant;
import java.util.UUID;

public record SignedRequest(
        String apiKey,
        String keyId,
        UUID requestId,
        Instant timestamp,
        String method,
        String rawTarget,
        byte[] body,
        String signature
) {}
