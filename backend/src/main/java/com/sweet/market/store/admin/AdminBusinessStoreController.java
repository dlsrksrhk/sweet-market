package com.sweet.market.store.admin;

import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.store.api.StorePrivateResponse;
import com.sweet.market.store.application.StoreGovernanceService;
import com.sweet.market.store.domain.StoreStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/business-stores")
public class AdminBusinessStoreController {

    private final AdminBusinessStoreQueryService queryService;
    private final StoreGovernanceService governanceService;

    public AdminBusinessStoreController(AdminBusinessStoreQueryService queryService, StoreGovernanceService governanceService) {
        this.queryService = queryService;
        this.governanceService = governanceService;
    }

    @GetMapping
    public ApiResponse<Page<AdminBusinessStoreResponse>> search(
            @RequestParam(required = false) StoreStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(queryService.search(status, pageable));
    }

    @GetMapping("/{storeId}")
    public ApiResponse<AdminBusinessStoreResponse> detail(@PathVariable Long storeId) {
        return ApiResponse.ok(queryService.findDetail(storeId));
    }

    @PostMapping("/{storeId}/approve")
    public ApiResponse<StorePrivateResponse> approve(@PathVariable Long storeId) {
        return ApiResponse.ok(governanceService.approve(storeId));
    }

    @PostMapping("/{storeId}/reject")
    public ApiResponse<StorePrivateResponse> reject(
            @PathVariable Long storeId,
            @Valid @RequestBody BusinessStoreRejectRequest request
    ) {
        return ApiResponse.ok(governanceService.reject(storeId, request.reason()));
    }

    @PostMapping("/{storeId}/suspend")
    public ApiResponse<StorePrivateResponse> suspend(@PathVariable Long storeId) {
        return ApiResponse.ok(governanceService.suspend(storeId));
    }

    @PostMapping("/{storeId}/reactivate")
    public ApiResponse<StorePrivateResponse> reactivate(@PathVariable Long storeId) {
        return ApiResponse.ok(governanceService.reactivate(storeId));
    }
}
