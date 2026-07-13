package com.sweet.market.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.sweet.market.catalog.domain.CatalogSort;
import com.sweet.market.product.domain.ProductCategory;

class CatalogSearchRequestTest {

    @Test
    void 기본_정렬과_페이지_크기를_사용하고_검색어를_정규화한다() {
        CatalogSearchRequest request = new CatalogSearchRequest(
                "  노트북  ", null, null, null, null, null, null, null, null, null, null
        );

        assertThat(request.normalizedKeyword()).isEqualTo("노트북");
        assertThat(request.resolvedSort()).isEqualTo(CatalogSort.NEWEST);
        assertThat(request.resolvedSize()).isEqualTo(12);
    }

    @Test
    void 페이지_크기는_40으로_제한한다() {
        CatalogSearchRequest request = new CatalogSearchRequest(
                null, null, null, null, null, null, null, null, null, null, 100
        );

        assertThat(request.resolvedSize()).isEqualTo(40);
    }

    @Test
    void 커서와_페이지_크기는_필터_지문에서_제외한다() {
        CatalogSearchRequest firstRequest = new CatalogSearchRequest(
                "  노트북  ", ProductCategory.COMPUTERS, 1_000L, 10_000L,
                null, null, null, 4L, CatalogSort.PRICE_ASC, "first-cursor", 12
        );
        CatalogSearchRequest nextRequest = new CatalogSearchRequest(
                "노트북", ProductCategory.COMPUTERS, 1_000L, 10_000L,
                null, null, null, 4L, CatalogSort.PRICE_ASC, "next-cursor", 40
        );

        assertThat(firstRequest.filterFingerprint(7L)).isEqualTo(nextRequest.filterFingerprint(7L));
    }

    @Test
    void 고정된_경로_상점은_필터_지문에_포함한다() {
        CatalogSearchRequest request = new CatalogSearchRequest(
                null, null, null, null, null, null, null, null, null, null, null
        );

        assertThat(request.filterFingerprint(1L)).isNotEqualTo(request.filterFingerprint(2L));
    }
}
