package com.sweet.market.settlement.admin;

import com.sweet.market.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/settlements")
public class AdminSettlementController {

    private final AdminSettlementQueryService adminSettlementQueryService;
    private final AdminSettlementRetryService adminSettlementRetryService;

    public AdminSettlementController(
            AdminSettlementQueryService adminSettlementQueryService,
            AdminSettlementRetryService adminSettlementRetryService
    ) {
        this.adminSettlementQueryService = adminSettlementQueryService;
        this.adminSettlementRetryService = adminSettlementRetryService;
    }

    @GetMapping
    public ApiResponse<Page<AdminSettlementSummaryResponse>> search(
            @ModelAttribute AdminSettlementSearchRequest request,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(adminSettlementQueryService.search(request, pageable));
    }

    @GetMapping("/{settlementId}")
    public ApiResponse<AdminSettlementDetailResponse> detail(@PathVariable Long settlementId) {
        return ApiResponse.ok(adminSettlementQueryService.findDetail(settlementId));
    }

    @PostMapping("/retry")
    public ApiResponse<AdminSettlementRetryResponse> retry(
            @Valid @RequestBody AdminSettlementRetryRequest request
    ) {
        return ApiResponse.ok(adminSettlementRetryService.retry(request));
    }
}
