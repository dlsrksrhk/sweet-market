package com.sweet.market.store.api;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.store.application.StoreGovernanceService;
import com.sweet.market.store.application.StoreQueryService;

import jakarta.validation.Valid;

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

    @GetMapping("/{storeId}")
    public ApiResponse<PublicStoreResponse> publicProfile(@PathVariable Long storeId) {
        return ApiResponse.ok(storeQueryService.findPublicProfile(storeId));
    }

    private Long memberId(Authentication authentication) {
        return ((AuthenticatedMember) authentication.getPrincipal()).id();
    }
}
