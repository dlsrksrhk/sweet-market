package com.sweet.market.store.operations;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/store-operations")
public class StoreOperationsController {

    private final StoreCatalogQueryService storeCatalogQueryService;

    public StoreOperationsController(StoreCatalogQueryService storeCatalogQueryService) {
        this.storeCatalogQueryService = storeCatalogQueryService;
    }

    @GetMapping
    public ApiResponse<List<OperableStoreResponse>> stores(Authentication authentication) {
        return ApiResponse.ok(storeCatalogQueryService.findOperableStores(memberId(authentication)));
    }

    @GetMapping("/{storeId}/summary")
    public ApiResponse<StoreCatalogSummaryResponse> summary(
            Authentication authentication,
            @PathVariable Long storeId
    ) {
        return ApiResponse.ok(storeCatalogQueryService.findSummary(memberId(authentication), storeId));
    }

    @GetMapping("/{storeId}/products")
    public ApiResponse<Page<StoreCatalogProductResponse>> products(
            Authentication authentication,
            @PathVariable Long storeId,
            @Valid @ModelAttribute StoreCatalogSearchRequest request
    ) {
        return ApiResponse.ok(storeCatalogQueryService.findProducts(memberId(authentication), storeId, request));
    }

    private Long memberId(Authentication authentication) {
        return ((AuthenticatedMember) authentication.getPrincipal()).id();
    }
}
