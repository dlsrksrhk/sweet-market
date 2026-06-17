package com.sweet.market.seller.report;

import com.sweet.market.order.domain.OrderStatus;

public interface SellerOrderStatusCountProjection {

    OrderStatus getStatus();

    long getCount();
}
