package com.sweet.market.store.operations;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/store-operations")
public class StoreOperationsController {

    private final StoreCatalogQueryService storeCatalogQueryService;
    private final StoreCatalogCommandService storeCatalogCommandService;
    private final StoreMembershipQueryService storeMembershipQueryService;
    private final StoreMembershipCommandService storeMembershipCommandService;

    public StoreOperationsController(
            StoreCatalogQueryService storeCatalogQueryService,
            StoreCatalogCommandService storeCatalogCommandService,
            StoreMembershipQueryService storeMembershipQueryService,
            StoreMembershipCommandService storeMembershipCommandService
    ) {
        this.storeCatalogQueryService = storeCatalogQueryService;
        this.storeCatalogCommandService = storeCatalogCommandService;
        this.storeMembershipQueryService = storeMembershipQueryService;
        this.storeMembershipCommandService = storeMembershipCommandService;
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

    @GetMapping("/{storeId}/memberships")
    public ApiResponse<List<StoreMembershipResponse>> memberships(
            Authentication authentication,
            @PathVariable Long storeId
    ) {
        return ApiResponse.ok(storeMembershipQueryService.findActive(memberId(authentication), storeId));
    }

    @DeleteMapping("/{storeId}/memberships/{membershipId}")
    public ApiResponse<Void> removeManager(
            Authentication authentication,
            @PathVariable Long storeId,
            @PathVariable Long membershipId
    ) {
        storeMembershipCommandService.removeManager(memberId(authentication), storeId, membershipId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/{storeId}/products")
    public ApiResponse<Page<StoreCatalogProductResponse>> products(
            Authentication authentication,
            @PathVariable Long storeId,
            @Valid @ModelAttribute StoreCatalogSearchRequest request
    ) {
        return ApiResponse.ok(storeCatalogQueryService.findProducts(memberId(authentication), storeId, request));
    }

    @PostMapping("/{storeId}/products/hide")
    public ApiResponse<Void> hide(
            Authentication authentication,
            @PathVariable Long storeId,
            @Valid @RequestBody StoreProductIdsRequest request
    ) {
        storeCatalogCommandService.hide(memberId(authentication), storeId, request.productIds());
        return ApiResponse.ok(null);
    }

    @PostMapping("/{storeId}/products/show")
    public ApiResponse<Void> show(
            Authentication authentication,
            @PathVariable Long storeId,
            @Valid @RequestBody StoreProductIdsRequest request
    ) {
        storeCatalogCommandService.show(memberId(authentication), storeId, request.productIds());
        return ApiResponse.ok(null);
    }

    private Long memberId(Authentication authentication) {
        return ((AuthenticatedMember) authentication.getPrincipal()).id();
    }
}
