package com.sweet.market.integration.security;

import java.time.Instant;
import java.util.UUID;

public interface ExternalRequestSigner {

    SignedHeaders sign(
            ExternalSystem destination,
            String method,
            String rawTarget,
            UUID requestId,
            Instant timestamp,
            byte[] body,
            String correlationId
    );
}
