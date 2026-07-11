package com.sweet.market.store.operations;

import com.sweet.market.product.domain.ProductStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record StoreCatalogSearchRequest(
        ProductStatus status,
        String keyword,
        StoreCatalogSort sort,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {

    public String normalizedKeyword() {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    public StoreCatalogSort resolvedSort() {
        return sort == null ? StoreCatalogSort.NEWEST : sort;
    }

    public int resolvedPage() {
        return page == null ? 0 : page;
    }

    public int resolvedSize() {
        return size == null ? 20 : size;
    }
}
