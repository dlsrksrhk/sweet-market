package com.sweet.market.coupon.api;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.coupon.application.CouponCampaignService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/coupon-campaigns")
public class AdminCouponCampaignController {
    private final CouponCampaignService service;

    public AdminCouponCampaignController(CouponCampaignService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponCampaignResponse> create(Authentication authentication, @Valid @RequestBody CouponCampaignCreateRequest request) {
        return ApiResponse.ok(service.createPlatformCampaign(memberId(authentication), request));
    }

    @GetMapping
    public ApiResponse<Page<CouponCampaignResponse>> findPage(@Valid @ModelAttribute CouponCampaignSearchRequest request) {
        return ApiResponse.ok(service.findPlatformPage(request));
    }

    @GetMapping("/{campaignId}")
    public ApiResponse<CouponCampaignResponse> find(@PathVariable Long campaignId) {
        return ApiResponse.ok(service.findPlatform(campaignId));
    }

    @PatchMapping("/{campaignId}")
    public ApiResponse<CouponCampaignResponse> update(Authentication authentication, @PathVariable Long campaignId, @Valid @RequestBody CouponCampaignUpdateRequest request) {
        return ApiResponse.ok(service.updatePlatform(memberId(authentication), campaignId, request));
    }

    @PostMapping("/{campaignId}/schedule")
    public ApiResponse<CouponCampaignResponse> schedule(Authentication authentication, @PathVariable Long campaignId) {
        return ApiResponse.ok(service.schedulePlatform(memberId(authentication), campaignId));
    }

    @PostMapping("/{campaignId}/pause")
    public ApiResponse<CouponCampaignResponse> pause(Authentication authentication, @PathVariable Long campaignId) {
        return ApiResponse.ok(service.pausePlatform(memberId(authentication), campaignId));
    }

    @PostMapping("/{campaignId}/resume")
    public ApiResponse<CouponCampaignResponse> resume(Authentication authentication, @PathVariable Long campaignId) {
        return ApiResponse.ok(service.resumePlatform(memberId(authentication), campaignId));
    }

    @PostMapping("/{campaignId}/end")
    public ApiResponse<CouponCampaignResponse> end(Authentication authentication, @PathVariable Long campaignId) {
        return ApiResponse.ok(service.endPlatform(memberId(authentication), campaignId));
    }

    private Long memberId(Authentication authentication) {
        return ((AuthenticatedMember) authentication.getPrincipal()).id();
    }
}
