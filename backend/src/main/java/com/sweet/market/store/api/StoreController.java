package com.sweet.market.store.api;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.store.application.StoreGovernanceService;
import com.sweet.market.store.application.StoreQueryService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreGovernanceService storeGovernanceService;
    private final StoreQueryService storeQueryService;

    public StoreController(StoreGovernanceService storeGovernanceService, StoreQueryService storeQueryService) {
        this.storeGovernanceService = storeGovernanceService;
        this.storeQueryService = storeQueryService;
    }

    @GetMapping("/me")
    public ApiResponse<List<StorePrivateResponse>> myStores(Authentication authentication) {
        return ApiResponse.ok(storeQueryService.findOwnedStores(memberId(authentication)));
    }

    @PostMapping("/business-applications")
    public ApiResponse<StorePrivateResponse> applyBusiness(
            Authentication authentication,
            @Valid @RequestBody BusinessStoreApplicationRequest request
    ) {
        return ApiResponse.ok(storeGovernanceService.applyBusiness(memberId(authentication), request));
    }

    @PatchMapping("/business-applications/{storeId}")
    public ApiResponse<StorePrivateResponse> resubmitBusiness(
            Authentication authentication,
            @PathVariable Long storeId,
            @Valid @RequestBody BusinessStoreApplicationRequest request
    ) {
        return ApiResponse.ok(storeGovernanceService.resubmitBusiness(memberId(authentication), storeId, request));
    }

    @PatchMapping("/{storeId}/profile")
    public ApiResponse<StorePrivateResponse> updateProfile(
            Authentication authentication,
            @PathVariable Long storeId,
            @Valid @RequestBody StoreProfileUpdateRequest request
    ) {
        return ApiResponse.ok(storeGovernanceService.updateProfile(memberId(authentication), storeId, request));
    }

    private Long memberId(Authentication authentication) {
        return ((AuthenticatedMember) authentication.getPrincipal()).id();
    }
}
