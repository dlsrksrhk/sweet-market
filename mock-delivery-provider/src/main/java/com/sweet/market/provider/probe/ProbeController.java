package com.sweet.market.provider.probe;

import com.sweet.market.provider.security.SignedRequestFilter;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/probes")
public class ProbeController {

    @PostMapping
    ProbeResponse probe(
            @Valid @RequestBody ProbeRequest request,
            @RequestAttribute(SignedRequestFilter.REQUEST_ID_ATTRIBUTE) UUID authenticatedRequestId,
            @RequestAttribute(SignedRequestFilter.CORRELATION_ID_ATTRIBUTE) UUID correlationId
    ) {
        return new ProbeResponse(
                "mock-delivery-provider",
                request.message(),
                authenticatedRequestId,
                correlationId);
    }
}
