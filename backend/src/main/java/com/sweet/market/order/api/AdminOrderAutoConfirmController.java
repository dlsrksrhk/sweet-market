package com.sweet.market.order.api;

import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.order.application.OrderAutoConfirmResult;
import com.sweet.market.order.application.OrderAutoConfirmService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/orders/auto-confirm")
public class AdminOrderAutoConfirmController {

    private final OrderAutoConfirmService orderAutoConfirmService;

    public AdminOrderAutoConfirmController(OrderAutoConfirmService orderAutoConfirmService) {
        this.orderAutoConfirmService = orderAutoConfirmService;
    }

    @PostMapping
    public ApiResponse<OrderAutoConfirmResponse> run() {
        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders();
        return ApiResponse.ok(OrderAutoConfirmResponse.from(result));
    }
}
