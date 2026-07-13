package com.sweet.market.catalog.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.catalog.query.CatalogSearchQueryService;
import com.sweet.market.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogSearchQueryService catalogSearchQueryService;

    public CatalogController(CatalogSearchQueryService catalogSearchQueryService) {
        this.catalogSearchQueryService = catalogSearchQueryService;
    }

    @GetMapping("/products")
    public ApiResponse<CatalogSearchResponse> products(
            Authentication authentication,
            @Valid @ModelAttribute CatalogSearchRequest request
    ) {
        return ApiResponse.ok(catalogSearchQueryService.search(authenticatedMemberId(authentication), request, null));
    }

    private Long authenticatedMemberId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedMember member)) {
            return null;
        }
        return member.id();
    }
}
