package com.sweet.market.delivery.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.delivery.application.DeliveryService;

@RestController
@RequestMapping("/api/deliveries")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping("/{orderId}/start")
    public ApiResponse<DeliveryResponse> start(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(deliveryService.start(member.id(), orderId));
    }

    @PostMapping("/{orderId}/complete")
    public ApiResponse<DeliveryResponse> complete(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(deliveryService.complete(member.id(), orderId));
    }
}
