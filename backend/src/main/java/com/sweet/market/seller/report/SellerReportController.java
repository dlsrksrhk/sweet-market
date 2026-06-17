package com.sweet.market.seller.report;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;

@RestController
@RequestMapping("/api/seller/reports")
public class SellerReportController {

    private final SellerReportQueryService sellerReportQueryService;

    public SellerReportController(SellerReportQueryService sellerReportQueryService) {
        this.sellerReportQueryService = sellerReportQueryService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<SellerDashboardReportResponse> dashboard(Authentication authentication) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(sellerReportQueryService.getDashboard(member.id()));
    }
}
