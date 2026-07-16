package com.sweet.market.discovery.api;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.catalog.api.CatalogProductCardResponse;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.discovery.domain.DiscoveryEventType;
import com.sweet.market.discovery.query.DiscoveryQueryService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private final DiscoveryQueryService discoveryQueryService;

    public DiscoveryController(DiscoveryQueryService discoveryQueryService) {
        this.discoveryQueryService = discoveryQueryService;
    }

    @GetMapping("/events")
    public ApiResponse<List<ActiveEventResponse>> events() {
        return ApiResponse.ok(discoveryQueryService.activeEvents());
    }

    @GetMapping("/events/{eventType}/{eventId}")
    public ApiResponse<EventDetailResponse> event(
            @PathVariable DiscoveryEventType eventType,
            @PathVariable Long eventId
    ) {
        return ApiResponse.ok(discoveryQueryService.event(eventType, eventId));
    }

    @GetMapping("/popular-products")
    public ApiResponse<List<CatalogProductCardResponse>> popularProducts(Authentication authentication) {
        return ApiResponse.ok(discoveryQueryService.popularProducts(authenticatedMemberId(authentication)));
    }

    private Long authenticatedMemberId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedMember member)) {
            return null;
        }
        return member.id();
    }
}
