package com.sweet.market.coupon.api;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.coupon.application.CouponIssueService;
import com.sweet.market.coupon.query.CouponDiscoveryQueryService;
import com.sweet.market.coupon.query.CouponWalletQueryService;

import jakarta.validation.Valid;

@RestController
public class CouponClaimController {
    private final CouponIssueService issueService;
    private final CouponDiscoveryQueryService discoveryQueryService;
    private final CouponWalletQueryService walletQueryService;

    public CouponClaimController(
            CouponIssueService issueService,
            CouponDiscoveryQueryService discoveryQueryService,
            CouponWalletQueryService walletQueryService
    ) {
        this.issueService = issueService;
        this.discoveryQueryService = discoveryQueryService;
        this.walletQueryService = walletQueryService;
    }

    @GetMapping("/api/coupon-campaigns/available")
    public ApiResponse<Page<AvailableCouponCampaignResponse>> findAvailable(
            Authentication authentication, @Valid @ModelAttribute AvailableCouponCampaignSearchRequest request
    ) {
        return ApiResponse.ok(discoveryQueryService.findAvailable(memberId(authentication), request));
    }

    @PostMapping("/api/coupon-campaigns/{campaignId}/claim")
    public ApiResponse<MemberCouponResponse> claim(Authentication authentication, @PathVariable Long campaignId) {
        return ApiResponse.ok(issueService.claim(memberId(authentication), campaignId));
    }

    @GetMapping("/api/me/coupons")
    public ApiResponse<Page<MemberCouponResponse>> findMine(
            Authentication authentication, @Valid @ModelAttribute MemberCouponSearchRequest request
    ) {
        return ApiResponse.ok(walletQueryService.findMine(memberId(authentication), request));
    }

    private Long memberId(Authentication authentication) {
        return ((AuthenticatedMember) authentication.getPrincipal()).id();
    }
}
