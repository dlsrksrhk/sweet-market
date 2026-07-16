package com.sweet.market.order.scheduler;

import com.sweet.market.order.application.OrderAutoConfirmResult;
import com.sweet.market.order.application.OrderAutoConfirmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(
        prefix = "market.order.auto-confirm",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OrderAutoConfirmScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoConfirmScheduler.class);

    private final OrderAutoConfirmService orderAutoConfirmService;

    public OrderAutoConfirmScheduler(OrderAutoConfirmService orderAutoConfirmService) {
        this.orderAutoConfirmService = orderAutoConfirmService;
    }

    @Scheduled(fixedDelayString = "${market.order.auto-confirm.fixed-delay:PT1H}")
    public void confirmDeliveredOrders() {
        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders();
        log.info(
                "Auto-confirmed delivered orders. confirmedCount={}, deliveredBefore={}, thresholdDays={}, executedAt={}",
                result.confirmedCount(),
                result.deliveredBefore(),
                result.thresholdDays(),
                result.executedAt()
        );
    }
}
