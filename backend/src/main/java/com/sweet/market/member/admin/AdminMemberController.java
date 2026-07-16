package com.sweet.market.member.admin;

import com.sweet.market.common.api.ApiResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final AdminMemberQueryService adminMemberQueryService;

    public AdminMemberController(AdminMemberQueryService adminMemberQueryService) {
        this.adminMemberQueryService = adminMemberQueryService;
    }

    @GetMapping
    public ApiResponse<Page<AdminMemberSummaryResponse>> search(
            @ModelAttribute AdminMemberSearchRequest request,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.ok(adminMemberQueryService.search(request, pageable));
    }

    @GetMapping("/{memberId}")
    public ApiResponse<AdminMemberDetailResponse> detail(@PathVariable Long memberId) {
        return ApiResponse.ok(adminMemberQueryService.findDetail(memberId));
    }
}
