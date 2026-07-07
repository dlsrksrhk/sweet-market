package com.sweet.market.refund.api;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.refund.application.RefundRequestService;
import com.sweet.market.refund.domain.RefundRequestStatus;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/refund-requests")
public class AdminRefundRequestController {

    private final RefundRequestService refundRequestService;

    public AdminRefundRequestController(RefundRequestService refundRequestService) {
        this.refundRequestService = refundRequestService;
    }

    @GetMapping
    public ApiResponse<List<RefundRequestResponse>> list(
            @RequestParam(required = false) RefundRequestStatus status
    ) {
        return ApiResponse.ok(refundRequestService.findAdminRequests(status));
    }

    @PostMapping("/{refundRequestId}/approve")
    public ApiResponse<RefundRequestResponse> approve(
            Authentication authentication,
            @PathVariable Long refundRequestId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.approveByAdmin(member.id(), refundRequestId));
    }

    @PostMapping("/{refundRequestId}/reject")
    public ApiResponse<RefundRequestResponse> reject(
            Authentication authentication,
            @PathVariable Long refundRequestId,
            @Valid @RequestBody RefundRejectRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.rejectByAdmin(member.id(), refundRequestId, request.rejectReason()));
    }
}
