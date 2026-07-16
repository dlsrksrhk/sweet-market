package com.sweet.market.promotion.api;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.promotion.application.PromotionCampaignService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/stores/{storeId}/promotions")
public class PromotionCampaignController {

    private final PromotionCampaignService promotionCampaignService;

    public PromotionCampaignController(PromotionCampaignService promotionCampaignService) {
        this.promotionCampaignService = promotionCampaignService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PromotionCampaignResponse> create(
            Authentication authentication,
            @PathVariable Long storeId,
            @Valid @RequestBody PromotionCampaignCreateRequest request
    ) {
        return ApiResponse.ok(promotionCampaignService.create(memberId(authentication), storeId, request));
    }

    @GetMapping
    public ApiResponse<Page<PromotionCampaignResponse>> findPage(
            Authentication authentication,
            @PathVariable Long storeId,
            @Valid @ModelAttribute PromotionCampaignSearchRequest request
    ) {
        return ApiResponse.ok(promotionCampaignService.findPage(memberId(authentication), storeId, request));
    }

    @GetMapping("/{promotionId}")
    public ApiResponse<PromotionCampaignResponse> find(
            Authentication authentication,
            @PathVariable Long storeId,
            @PathVariable Long promotionId
    ) {
        return ApiResponse.ok(promotionCampaignService.find(memberId(authentication), storeId, promotionId));
    }

    @PatchMapping("/{promotionId}")
    public ApiResponse<PromotionCampaignResponse> update(
            Authentication authentication,
            @PathVariable Long storeId,
            @PathVariable Long promotionId,
            @Valid @RequestBody PromotionCampaignUpdateRequest request
    ) {
        return ApiResponse.ok(promotionCampaignService.update(memberId(authentication), storeId, promotionId, request));
    }

    @PostMapping("/{promotionId}/schedule")
    public ApiResponse<PromotionCampaignResponse> schedule(Authentication authentication, @PathVariable Long storeId, @PathVariable Long promotionId) {
        return ApiResponse.ok(promotionCampaignService.schedule(memberId(authentication), storeId, promotionId));
    }

    @PostMapping("/{promotionId}/pause")
    public ApiResponse<PromotionCampaignResponse> pause(Authentication authentication, @PathVariable Long storeId, @PathVariable Long promotionId) {
        return ApiResponse.ok(promotionCampaignService.pause(memberId(authentication), storeId, promotionId));
    }

    @PostMapping("/{promotionId}/resume")
    public ApiResponse<PromotionCampaignResponse> resume(Authentication authentication, @PathVariable Long storeId, @PathVariable Long promotionId) {
        return ApiResponse.ok(promotionCampaignService.resume(memberId(authentication), storeId, promotionId));
    }

    @PostMapping("/{promotionId}/end")
    public ApiResponse<PromotionCampaignResponse> end(Authentication authentication, @PathVariable Long storeId, @PathVariable Long promotionId) {
        return ApiResponse.ok(promotionCampaignService.end(memberId(authentication), storeId, promotionId));
    }

    private Long memberId(Authentication authentication) {
        return ((AuthenticatedMember) authentication.getPrincipal()).id();
    }
}
