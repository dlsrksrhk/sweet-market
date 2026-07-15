package com.sweet.market.coupon.api;

import org.springframework.data.domain.Page;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.coupon.application.CouponIssueService;
import com.sweet.market.coupon.query.CouponDiscoveryQueryService;
import com.sweet.market.coupon.query.CouponWalletQueryService;
import com.sweet.market.coupon.query.CouponEligibilityQueryService;

import java.util.List;

import jakarta.validation.Valid;

@RestController
public class CouponClaimController {
    private final CouponIssueService issueService;
    private final CouponDiscoveryQueryService discoveryQueryService;
    private final CouponWalletQueryService walletQueryService;
    private final CouponEligibilityQueryService eligibilityQueryService;

    public CouponClaimController(
            CouponIssueService issueService,
            CouponDiscoveryQueryService discoveryQueryService,
            CouponWalletQueryService walletQueryService,
            CouponEligibilityQueryService eligibilityQueryService
    ) {
        this.issueService = issueService;
        this.discoveryQueryService = discoveryQueryService;
        this.walletQueryService = walletQueryService;
        this.eligibilityQueryService = eligibilityQueryService;
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

    @GetMapping("/api/me/coupons/eligible")
    public ApiResponse<List<EligibleMemberCouponResponse>> findEligible(Authentication authentication,
                                                                         @RequestParam Long productId) {
        return ApiResponse.ok(eligibilityQueryService.findEligible(memberId(authentication), productId));
    }

    private Long memberId(Authentication authentication) {
        return ((AuthenticatedMember) authentication.getPrincipal()).id();
    }
}
