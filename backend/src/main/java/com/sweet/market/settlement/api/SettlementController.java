package com.sweet.market.settlement.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.settlement.application.SettlementService;
import com.sweet.market.settlement.query.SettlementQueryService;

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
