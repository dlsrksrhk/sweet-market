package com.sweet.market.catalog.query;

import com.sweet.market.catalog.api.CatalogSearchRequest;
import com.sweet.market.catalog.domain.CatalogAvailabilityFilter;
import com.sweet.market.catalog.domain.CatalogSort;
import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.store.domain.StoreType;

import java.util.Objects;

public record CatalogSearchCriteria(
        String keyword,
        ProductCategory category,
        Long minPrice,
        Long maxPrice,
        CatalogAvailabilityFilter availability,
        ProductSalesPolicy salesPolicy,
        StoreType storeType,
        Long storeId,
        CatalogSort sort,
        int size
) {

    public CatalogSearchCriteria {
        keyword = keyword == null ? null : keyword.trim();
        Objects.requireNonNull(sort, "sort must not be null");
        if (size < 1) {
            throw new IllegalArgumentException("size must be positive");
        }
    }

    public static CatalogSearchCriteria from(CatalogSearchRequest request, Long fixedStoreId) {
        return new CatalogSearchCriteria(
                request.normalizedKeyword(),
                request.category(),
                request.minPrice(),
                request.maxPrice(),
                request.availability(),
                request.salesPolicy(),
                request.storeType(),
                fixedStoreId == null ? request.storeId() : fixedStoreId,
                request.resolvedSort(),
                request.resolvedSize()
        );
    }
}
