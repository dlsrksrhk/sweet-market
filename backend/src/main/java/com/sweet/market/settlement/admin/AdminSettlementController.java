package com.sweet.market.settlement.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;

@RestController
@RequestMapping("/api/admin/settlements")
public class AdminSettlementController {

    private final AdminSettlementQueryService adminSettlementQueryService;

    public AdminSettlementController(AdminSettlementQueryService adminSettlementQueryService) {
        this.adminSettlementQueryService = adminSettlementQueryService;
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
}
