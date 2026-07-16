package com.sweet.market.settlement.api;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.settlement.application.SettlementService;
import com.sweet.market.settlement.query.SettlementQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementQueryService settlementQueryService;

    public SettlementController(
            SettlementService settlementService,
            SettlementQueryService settlementQueryService
    ) {
        this.settlementService = settlementService;
        this.settlementQueryService = settlementQueryService;
    }

    @PostMapping("/orders/{orderId}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SettlementResponse> create(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(settlementService.create(member.id(), orderId));
    }

    @GetMapping("/me")
    public ApiResponse<List<SettlementResponse>> findMine(Authentication authentication) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(settlementQueryService.findMine(member.id()));
    }
}
