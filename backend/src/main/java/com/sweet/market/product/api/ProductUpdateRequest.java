package com.sweet.market.product.api;

import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProductUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String title,

        @NotBlank
        @Size(max = 2000)
        String description,

        @Positive
        long price,

        ProductSalesPolicy salesPolicy,

        Integer lowStockThreshold,

        @NotNull
        @Size(max = 10)
        List<@Valid ProductUpdateImageRequest> images,

        ProductCategory category
) {

    public ProductUpdateRequest(
            String title,
            String description,
            long price,
            ProductSalesPolicy salesPolicy,
            Integer lowStockThreshold,
            List<ProductUpdateImageRequest> images
    ) {
        this(title, description, price, salesPolicy, lowStockThreshold, images, null);
    }
}
