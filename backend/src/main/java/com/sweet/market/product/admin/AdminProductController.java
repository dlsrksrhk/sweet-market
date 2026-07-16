package com.sweet.market.product.admin;

import com.sweet.market.common.api.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final AdminProductQueryService adminProductQueryService;
    private final AdminProductService adminProductService;

    public AdminProductController(
            AdminProductQueryService adminProductQueryService,
            AdminProductService adminProductService
    ) {
        this.adminProductQueryService = adminProductQueryService;
        this.adminProductService = adminProductService;
    }

    @GetMapping
    public ApiResponse<Page<AdminProductSummaryResponse>> search(
            @ModelAttribute AdminProductSearchRequest request,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(adminProductQueryService.search(request, pageable));
    }

    @GetMapping("/{productId}")
    public ApiResponse<AdminProductDetailResponse> detail(@PathVariable Long productId) {
        return ApiResponse.ok(adminProductQueryService.findDetail(productId));
    }

    @PostMapping("/{productId}/hide")
    public ApiResponse<AdminProductDetailResponse> hide(@PathVariable Long productId) {
        return ApiResponse.ok(adminProductService.hide(productId));
    }
}
