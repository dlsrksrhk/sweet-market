package com.sweet.market.gateway.probe;

import com.sweet.market.gateway.security.SignedRequestFilter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/probes")
public class ProbeController {

    @PostMapping
    ProbeResponse probe(
            @Valid @RequestBody ProbeRequest request,
            @RequestAttribute(SignedRequestFilter.REQUEST_ID_ATTRIBUTE) UUID requestId,
            @RequestHeader("X-Correlation-Id") UUID correlationId
    ) {
        return new ProbeResponse(
                "mock-payment-gateway",
                request.message(),
                requestId,
                correlationId
        );
    }
}
