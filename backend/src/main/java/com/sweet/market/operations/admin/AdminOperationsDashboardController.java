package com.sweet.market.operations.admin;

import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.operations.store.StoreCampaignAuditResponse;
import com.sweet.market.operations.store.StoreCampaignMetricResponse;
import com.sweet.market.operations.store.StoreInventoryPressureResponse;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/operations-dashboard")
public class AdminOperationsDashboardController {

    private final AdminOperationsDashboardQueryService queryService;

    public AdminOperationsDashboardController(AdminOperationsDashboardQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    public ApiResponse<AdminOperationsDashboardResponse> dashboard(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId
    ) {
        return ApiResponse.ok(queryService.dashboard(preset, from, to, storeId));
    }

    @GetMapping("/campaigns")
    public ApiResponse<Page<StoreCampaignMetricResponse>> campaigns(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) String campaignKind,
            @RequestParam(required = false) String campaignStatus,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.campaigns(
                preset, from, to, storeId, ownerType, campaignKind, campaignStatus, page, size));
    }

    @GetMapping("/outcomes")
    public ApiResponse<Page<AdminOperationsDashboardResponse.OutcomeResponse>> outcomes(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) String campaignKind,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.outcomes(
                preset, from, to, storeId, ownerType, campaignKind, productId, reason, page, size));
    }

    @GetMapping("/inventory-pressure")
    public ApiResponse<Page<StoreInventoryPressureResponse>> inventoryPressure(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "true") boolean attentionOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.inventoryPressure(
                preset, from, to, storeId, productId, attentionOnly, page, size));
    }

    @GetMapping("/audits")
    public ApiResponse<Page<StoreCampaignAuditResponse>> audits(
            @RequestParam(required = false) String preset,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) String campaignKind,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.ok(queryService.audits(
                preset, from, to, storeId, ownerType, campaignKind, page, size));
    }
}
