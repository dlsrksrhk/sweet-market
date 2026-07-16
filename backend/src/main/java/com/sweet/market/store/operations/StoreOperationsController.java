package com.sweet.market.store.operations;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.inventory.application.InventoryAdjustmentRequest;
import com.sweet.market.inventory.application.InventoryAdjustmentResponse;
import com.sweet.market.inventory.application.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/store-operations")
public class StoreOperationsController {

    private final StoreCatalogQueryService storeCatalogQueryService;
    private final StoreCatalogCommandService storeCatalogCommandService;
    private final StoreMembershipQueryService storeMembershipQueryService;
    private final StoreMembershipCommandService storeMembershipCommandService;
    private final InventoryService inventoryService;

    public StoreOperationsController(
            StoreCatalogQueryService storeCatalogQueryService,
            StoreCatalogCommandService storeCatalogCommandService,
            StoreMembershipQueryService storeMembershipQueryService,
            StoreMembershipCommandService storeMembershipCommandService,
            InventoryService inventoryService
    ) {
        this.storeCatalogQueryService = storeCatalogQueryService;
        this.storeCatalogCommandService = storeCatalogCommandService;
        this.storeMembershipQueryService = storeMembershipQueryService;
        this.storeMembershipCommandService = storeMembershipCommandService;
        this.inventoryService = inventoryService;
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

    @PatchMapping("/{storeId}/products/{productId}/inventory")
    public ApiResponse<InventoryAdjustmentResponse> adjustInventory(
            Authentication authentication,
            @PathVariable Long storeId,
            @PathVariable Long productId,
            @Valid @RequestBody InventoryAdjustmentRequest request
    ) {
        return ApiResponse.ok(inventoryService.adjust(memberId(authentication), storeId, productId, request));
    }

    @GetMapping("/{storeId}/products/{productId}/inventory/history")
    public ApiResponse<Page<InventoryAdjustmentResponse>> inventoryHistory(
            Authentication authentication,
            @PathVariable Long storeId,
            @PathVariable Long productId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(inventoryService.history(memberId(authentication), storeId, productId, page, size));
    }

    private Long memberId(Authentication authentication) {
        return ((AuthenticatedMember) authentication.getPrincipal()).id();
    }
}
