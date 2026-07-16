package com.sweet.market.order.api;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.order.application.OrderService;
import com.sweet.market.order.query.OrderQueryService;
import com.sweet.market.purchase.application.DirectPurchaseCommand;
import com.sweet.market.purchase.application.PurchaseReservationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;
    private final OrderQueryService orderQueryService;
    private final PurchaseReservationService purchaseReservationService;

    public OrderController(
            OrderService orderService,
            OrderQueryService orderQueryService,
            PurchaseReservationService purchaseReservationService
    ) {
        this.orderService = orderService;
        this.orderQueryService = orderQueryService;
        this.purchaseReservationService = purchaseReservationService;
    }

    @GetMapping("/me")
    public ApiResponse<Page<OrderSummaryResponse>> listMine(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(orderQueryService.findMine(member.id(), pageable));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<OrderResponse> get(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(orderQueryService.findOne(member.id(), orderId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> create(
            Authentication authentication,
            @Valid @RequestBody OrderCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(purchaseReservationService.purchaseDirect(
                new DirectPurchaseCommand(member.id(), request.productId(), request.memberCouponId()),
                idempotencyKey
        ));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<OrderResponse> cancel(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(orderService.cancel(member.id(), orderId));
    }

    @PostMapping("/{orderId}/confirm")
    public ApiResponse<OrderResponse> confirm(
            Authentication authentication,
            @PathVariable Long orderId
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(orderService.confirm(member.id(), orderId));
    }
}
