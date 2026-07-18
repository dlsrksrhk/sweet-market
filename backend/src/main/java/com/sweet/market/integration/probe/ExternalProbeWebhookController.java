package com.sweet.market.integration.probe;

import com.sweet.market.integration.security.ExternalSystem;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "market.external-integrations.enabled", havingValue = "true")
public class ExternalProbeWebhookController {

    @PostMapping("/api/integrations/payment-gateway/v1/probes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void paymentProbe(@Valid @RequestBody ProbeWebhookRequest request) {
        requireSource(request, ExternalSystem.PAYMENT_GATEWAY);
    }

    @PostMapping("/api/integrations/delivery-provider/v1/probes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deliveryProbe(@Valid @RequestBody ProbeWebhookRequest request) {
        requireSource(request, ExternalSystem.DELIVERY_PROVIDER);
    }

    private void requireSource(ProbeWebhookRequest request, ExternalSystem expected) {
        if (request.source() != expected) {
            throw new InvalidProbeWebhookException("Webhook source does not match request path");
        }
    }

    public static final class InvalidProbeWebhookException extends RuntimeException {
        public InvalidProbeWebhookException(String message) {
            super(message);
        }
    }
}
