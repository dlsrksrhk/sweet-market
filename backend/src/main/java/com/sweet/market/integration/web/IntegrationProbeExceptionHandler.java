package com.sweet.market.integration.web;

import com.sweet.market.integration.probe.ExternalProbeWebhookController;
import com.sweet.market.integration.security.SignedWebhookFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

@RestControllerAdvice(assignableTypes = ExternalProbeWebhookController.class)
@ConditionalOnProperty(name = "market.external-integrations.enabled", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class IntegrationProbeExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<IntegrationErrorResponse> invalidBody(HttpServletRequest request) {
        return invalid(request, "Webhook request body is invalid");
    }

    @ExceptionHandler(ExternalProbeWebhookController.InvalidProbeWebhookException.class)
    ResponseEntity<IntegrationErrorResponse> invalidSource(
            ExternalProbeWebhookController.InvalidProbeWebhookException exception,
            HttpServletRequest request
    ) {
        return invalid(request, exception.getMessage());
    }

    private ResponseEntity<IntegrationErrorResponse> invalid(
            HttpServletRequest request,
            String message
    ) {
        UUID requestId = (UUID) request.getAttribute(SignedWebhookFilter.REQUEST_ID_ATTRIBUTE);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new IntegrationErrorResponse(
                        "INTEGRATION_REQUEST_INVALID", message, requestId));
    }
}
