package com.sweet.market.purchase.application;

import java.util.List;

public record CartPurchaseCommand(
        Long buyerId,
        List<Long> cartItemIds
) {
}
