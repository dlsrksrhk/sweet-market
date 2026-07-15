package com.sweet.market.coupon.api;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.coupon.application.CouponCampaignService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/stores/{storeId}/coupon-campaigns")
public class CouponCampaignController {
    private final CouponCampaignService service;
    public CouponCampaignController(CouponCampaignService service) { this.service = service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponCampaignResponse> create(Authentication authentication, @PathVariable Long storeId, @Valid @RequestBody CouponCampaignCreateRequest request) { return ApiResponse.ok(service.createStoreCampaign(memberId(authentication), storeId, request)); }
    @GetMapping public ApiResponse<Page<CouponCampaignResponse>> findPage(Authentication authentication, @PathVariable Long storeId, @Valid @ModelAttribute CouponCampaignSearchRequest request) { return ApiResponse.ok(service.findStorePage(memberId(authentication), storeId, request)); }
    @GetMapping("/{campaignId}") public ApiResponse<CouponCampaignResponse> find(Authentication authentication, @PathVariable Long storeId, @PathVariable Long campaignId) { return ApiResponse.ok(service.findStore(memberId(authentication), storeId, campaignId)); }
    @PatchMapping("/{campaignId}") public ApiResponse<CouponCampaignResponse> update(Authentication authentication, @PathVariable Long storeId, @PathVariable Long campaignId, @Valid @RequestBody CouponCampaignUpdateRequest request) { return ApiResponse.ok(service.updateStore(memberId(authentication), storeId, campaignId, request)); }
    @PostMapping("/{campaignId}/schedule") public ApiResponse<CouponCampaignResponse> schedule(Authentication authentication, @PathVariable Long storeId, @PathVariable Long campaignId) { return ApiResponse.ok(service.scheduleStore(memberId(authentication), storeId, campaignId)); }
    @PostMapping("/{campaignId}/pause") public ApiResponse<CouponCampaignResponse> pause(Authentication authentication, @PathVariable Long storeId, @PathVariable Long campaignId) { return ApiResponse.ok(service.pauseStore(memberId(authentication), storeId, campaignId)); }
    @PostMapping("/{campaignId}/resume") public ApiResponse<CouponCampaignResponse> resume(Authentication authentication, @PathVariable Long storeId, @PathVariable Long campaignId) { return ApiResponse.ok(service.resumeStore(memberId(authentication), storeId, campaignId)); }
    @PostMapping("/{campaignId}/end") public ApiResponse<CouponCampaignResponse> end(Authentication authentication, @PathVariable Long storeId, @PathVariable Long campaignId) { return ApiResponse.ok(service.endStore(memberId(authentication), storeId, campaignId)); }
    private Long memberId(Authentication authentication) { return ((AuthenticatedMember) authentication.getPrincipal()).id(); }
}
