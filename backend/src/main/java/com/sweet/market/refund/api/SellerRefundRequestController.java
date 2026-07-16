package com.sweet.market.refund.api;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.refund.application.RefundRequestService;
import com.sweet.market.refund.domain.RefundRequestStatus;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seller/refund-requests")
public class SellerRefundRequestController {

    private final RefundRequestService refundRequestService;

    public SellerRefundRequestController(RefundRequestService refundRequestService) {
        this.refundRequestService = refundRequestService;
    }

    @GetMapping
    public ApiResponse<Page<RefundRequestResponse>> list(
            Authentication authentication,
            @RequestParam(required = false) RefundRequestStatus status,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.findSellerRequests(member.id(), status, pageable));
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
