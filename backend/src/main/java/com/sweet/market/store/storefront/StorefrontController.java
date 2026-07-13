package com.sweet.market.store.storefront;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.catalog.api.CatalogSearchRequest;
import com.sweet.market.catalog.api.CatalogSearchResponse;
import com.sweet.market.catalog.query.CatalogSearchQueryService;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.product.domain.ProductStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/stores")
public class StorefrontController {

    private final StorefrontQueryService storefrontQueryService;
    private final CatalogSearchQueryService catalogSearchQueryService;

    public StorefrontController(
            StorefrontQueryService storefrontQueryService,
            CatalogSearchQueryService catalogSearchQueryService
    ) {
        this.storefrontQueryService = storefrontQueryService;
        this.catalogSearchQueryService = catalogSearchQueryService;
    }

    @GetMapping("/{storeId}")
    public ApiResponse<StorefrontResponse> storefront(@PathVariable long storeId) {
        return ApiResponse.ok(storefrontQueryService.findStorefront(storeId));
    }

    @GetMapping("/{storeId}/products")
    public ApiResponse<Page<StorefrontProductResponse>> products(
            Authentication authentication,
            @PathVariable long storeId,
            @RequestParam(defaultValue = "ON_SALE") ProductStatus status,
            @RequestParam(defaultValue = "NEWEST") StorefrontProductSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(40) int size
    ) {
        return ApiResponse.ok(storefrontQueryService.findProducts(
                storeId,
                status,
                sort,
                page,
                size,
                authenticatedMemberId(authentication)
        ));
    }

    @GetMapping("/{storeId}/catalog/products")
    public ApiResponse<CatalogSearchResponse> catalogProducts(
            Authentication authentication,
            @PathVariable long storeId,
            HttpServletRequest httpServletRequest,
            @Valid @org.springframework.web.bind.annotation.ModelAttribute CatalogSearchRequest request
    ) {
        return ApiResponse.ok(catalogSearchQueryService.search(
                authenticatedMemberId(authentication),
                httpServletRequest.getParameter("storeId") == null ? withoutStoreId(request) : request,
                storeId
        ));
    }

    private CatalogSearchRequest withoutStoreId(CatalogSearchRequest request) {
        return new CatalogSearchRequest(
                request.keyword(), request.category(), request.minPrice(), request.maxPrice(), request.availability(),
                request.salesPolicy(), request.storeType(), null, request.sort(), request.cursor(), request.size()
        );
    }

    private Long authenticatedMemberId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedMember member)) {
            return null;
        }
        return member.id();
    }
}
