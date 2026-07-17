package com.sweet.market.operations.store;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/stores/{storeId}/operations")
public class StoreOperationsDashboardController {

    private final StoreOperationsDashboardQueryService queryService;

    public StoreOperationsDashboardController(StoreOperationsDashboardQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<StoreOperationsDashboardResponse> dashboard(
            Authentication authentication,
            @PathVariable Long storeId,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ApiResponse.ok(queryService.dashboard(memberId(authentication), storeId, preset, from, to));
    }

    @GetMapping("/campaigns")
    public ApiResponse<Page<StoreCampaignMetricResponse>> campaigns(
            Authentication authentication,
            @PathVariable Long storeId,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String campaignKind,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.campaigns(
                memberId(authentication), storeId, preset, from, to, campaignKind, status, page, size));
    }

    @GetMapping("/coupon-outcomes")
    public ApiResponse<Page<StoreCouponOutcomeResponse>> couponOutcomes(
            Authentication authentication,
            @PathVariable Long storeId,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.couponOutcomes(
                memberId(authentication), storeId, preset, from, to, reason, page, size));
    }

    @GetMapping("/inventory-pressure")
    public ApiResponse<Page<StoreInventoryPressureResponse>> inventoryPressure(
            Authentication authentication,
            @PathVariable Long storeId,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "true") boolean attentionOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.inventoryPressure(
                memberId(authentication), storeId, preset, from, to, attentionOnly, page, size));
    }

    @GetMapping("/purchase-outcomes")
    public ApiResponse<Page<StorePurchaseOutcomeResponse>> purchaseOutcomes(
            Authentication authentication,
            @PathVariable Long storeId,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.purchaseOutcomes(
                memberId(authentication), storeId, preset, from, to, reason, page, size));
    }

    @GetMapping("/campaign-audits")
    public ApiResponse<Page<StoreCampaignAuditResponse>> campaignAudits(
            Authentication authentication,
            @PathVariable Long storeId,
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String campaignKind,
            @RequestParam(required = false) String command,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.campaignAudits(
                memberId(authentication), storeId, preset, from, to, campaignKind, command, page, size));
    }

    private Long memberId(Authentication authentication) {
        return ((AuthenticatedMember) authentication.getPrincipal()).id();
    }
}
