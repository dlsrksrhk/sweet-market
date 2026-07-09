package com.sweet.market.refund.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.refund.application.RefundRequestService;
import com.sweet.market.refund.domain.RefundRequestStatus;

@RestController
@RequestMapping("/api/refund-requests")
public class BuyerRefundRequestController {

    private final RefundRequestService refundRequestService;

    public BuyerRefundRequestController(RefundRequestService refundRequestService) {
        this.refundRequestService = refundRequestService;
    }

    @GetMapping("/me")
    public ApiResponse<Page<RefundRequestResponse>> listMine(
            Authentication authentication,
            @RequestParam(required = false) RefundRequestStatus status,
            @PageableDefault(size = 20, sort = "requestedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.findBuyerRequests(member.id(), status, pageable));
    }
}
