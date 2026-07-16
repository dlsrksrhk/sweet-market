package com.sweet.market.coupon.api;

import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.coupon.application.CouponCampaignService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
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
    public ApiResponse<CouponCampaignResponse> create(@Valid @RequestBody CouponCampaignCreateRequest request) {
        return ApiResponse.ok(service.createPlatformCampaign(request));
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
    public ApiResponse<CouponCampaignResponse> update(@PathVariable Long campaignId, @Valid @RequestBody CouponCampaignUpdateRequest request) {
        return ApiResponse.ok(service.updatePlatform(campaignId, request));
    }

    @PostMapping("/{campaignId}/schedule")
    public ApiResponse<CouponCampaignResponse> schedule(@PathVariable Long campaignId) {
        return ApiResponse.ok(service.schedulePlatform(campaignId));
    }

    @PostMapping("/{campaignId}/pause")
    public ApiResponse<CouponCampaignResponse> pause(@PathVariable Long campaignId) {
        return ApiResponse.ok(service.pausePlatform(campaignId));
    }

    @PostMapping("/{campaignId}/resume")
    public ApiResponse<CouponCampaignResponse> resume(@PathVariable Long campaignId) {
        return ApiResponse.ok(service.resumePlatform(campaignId));
    }

    @PostMapping("/{campaignId}/end")
    public ApiResponse<CouponCampaignResponse> end(@PathVariable Long campaignId) {
        return ApiResponse.ok(service.endPlatform(campaignId));
    }
}
