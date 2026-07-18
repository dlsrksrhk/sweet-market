package com.sweet.market.integration.web;

import java.util.UUID;

public record IntegrationErrorResponse(String code, String message, UUID requestId) {
}
