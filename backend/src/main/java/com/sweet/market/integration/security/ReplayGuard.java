package com.sweet.market.integration.security;

import java.time.Instant;
import java.util.UUID;

public interface ReplayGuard {

    boolean tryClaim(ExternalSystem source, UUID requestId, Instant receivedAt, Instant expiresAt);

    int deleteExpired(Instant cutoff, int limit);
}
