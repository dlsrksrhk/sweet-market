package com.sweet.market.refund.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.refund.application.RefundRequestService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/seller/refund-requests")
public class SellerRefundRequestController {

    private final RefundRequestService refundRequestService;

    public SellerRefundRequestController(RefundRequestService refundRequestService) {
        this.refundRequestService = refundRequestService;
    }

    @PostMapping("/{refundRequestId}/approve")
    public ApiResponse<RefundRequestResponse> approve(
            Authentication authentication,
            @PathVariable Long refundRequestId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.approveBySeller(member.id(), refundRequestId));
    }

    @PostMapping("/{refundRequestId}/reject")
    public ApiResponse<RefundRequestResponse> reject(
            Authentication authentication,
            @PathVariable Long refundRequestId,
            @Valid @RequestBody RefundRejectRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.rejectBySeller(member.id(), refundRequestId, request.rejectReason()));
    }
}
