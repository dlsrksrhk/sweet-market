package com.sweet.market.catalog.api;

import java.util.List;

public record CatalogSearchResponse(
        List<CatalogProductCardResponse> content,
        boolean hasNext,
        String nextCursor
) {
}
