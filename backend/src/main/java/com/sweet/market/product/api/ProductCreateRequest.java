package com.sweet.market.product.api;

import java.util.List;

import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ProductCreateRequest(
        @NotNull
        Long storeId,

        @NotBlank
        @Size(max = 100)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @Positive
        long price,

        @NotNull
        ProductSalesPolicy salesPolicy,

        Integer initialTotalQuantity,

        Integer lowStockThreshold,

        @NotNull
        @Size(max = 10)
        List<@Valid ProductCreateImageRequest> images,

        ProductCategory category
) {

    public ProductCreateRequest(
            Long storeId,
            String title,
            String description,
            long price,
            ProductSalesPolicy salesPolicy,
            Integer initialTotalQuantity,
            Integer lowStockThreshold,
            List<ProductCreateImageRequest> images
    ) {
        this(storeId, title, description, price, salesPolicy, initialTotalQuantity, lowStockThreshold, images, null);
    }
}
