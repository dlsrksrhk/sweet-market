package com.sweet.market.seller.report;

import com.sweet.market.product.domain.ProductStatus;

public interface SellerProductStatusCountProjection {

    ProductStatus getStatus();

    long getCount();
}
