package com.sweet.market.gateway.web;

import java.util.UUID;

public record IntegrationErrorResponse(String code, String message, UUID requestId) {}
