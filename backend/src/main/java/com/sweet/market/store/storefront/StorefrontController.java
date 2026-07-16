package com.sweet.market.store.storefront;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.catalog.api.CatalogSearchRequest;
import com.sweet.market.catalog.api.CatalogSearchResponse;
import com.sweet.market.catalog.query.CatalogSearchQueryService;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.product.domain.ProductStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
        if (httpServletRequest.getParameterMap().containsKey("storeId")) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return ApiResponse.ok(catalogSearchQueryService.search(
                authenticatedMemberId(authentication),
                withoutStoreId(request),
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
