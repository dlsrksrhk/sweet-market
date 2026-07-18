package com.sweet.market.gateway.security;

import java.time.Instant;
import java.util.UUID;

public interface ReplayGuard {
    boolean tryClaim(String clientId, UUID requestId, Instant receivedAt, Instant expiresAt);

    int deleteExpired(Instant now, int limit);
}
