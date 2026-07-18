package com.sweet.market.provider.web;

import java.util.UUID;

public record IntegrationErrorResponse(String code, String message, UUID requestId) {}
