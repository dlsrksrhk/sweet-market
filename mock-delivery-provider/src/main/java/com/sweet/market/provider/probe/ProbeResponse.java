package com.sweet.market.provider.probe;

import java.util.UUID;

public record ProbeResponse(String service, String message, UUID requestId, UUID correlationId) {}
