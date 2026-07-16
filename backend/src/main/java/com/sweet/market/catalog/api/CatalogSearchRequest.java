package com.sweet.market.catalog.api;

import com.sweet.market.catalog.domain.CatalogAvailabilityFilter;
import com.sweet.market.catalog.domain.CatalogSort;
import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.store.domain.StoreType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public record CatalogSearchRequest(
        @Pattern(regexp = ".*\\S.*", message = "keyword must not be blank")
        String keyword,
        ProductCategory category,
        @Min(0) Long minPrice,
        @Min(0) Long maxPrice,
        CatalogAvailabilityFilter availability,
        ProductSalesPolicy salesPolicy,
        StoreType storeType,
        Long storeId,
        CatalogSort sort,
        String cursor,
        @Min(1) Integer size
) {

    private static final int DEFAULT_SIZE = 12;
    private static final int MAX_SIZE = 40;

    public CatalogSearchRequest {
        if (keyword != null) {
            keyword = keyword.trim();
        }
    }

    public String normalizedKeyword() {
        return keyword;
    }

    public CatalogSort resolvedSort() {
        return sort == null ? CatalogSort.NEWEST : sort;
    }

    public int resolvedSize() {
        return size == null ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    @AssertTrue(message = "minPrice must not exceed maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice <= maxPrice;
    }

    public String filterFingerprint() {
        return filterFingerprint(null);
    }

    public String filterFingerprint(Long routeStoreId) {
        String filterValues = String.join("|",
                value("keyword", normalizedKeyword()),
                value("category", category),
                value("minPrice", minPrice),
                value("maxPrice", maxPrice),
                value("availability", availability),
                value("salesPolicy", salesPolicy),
                value("storeType", storeType),
                value("storeId", storeId),
                value("sort", resolvedSort()),
                value("routeStoreId", routeStoreId)
        );
        return sha256(filterValues);
    }

    private static String value(String name, Object value) {
        String text = value == null ? "<null>" : value.toString();
        return name + ':' + text.length() + ':' + text;
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }
}
