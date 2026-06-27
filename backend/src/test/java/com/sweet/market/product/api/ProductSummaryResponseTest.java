package com.sweet.market.product.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;

class ProductSummaryResponseTest {

    @Test
    void 상품_요약_응답은_대표_이미지를_썸네일로_사용한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.replaceImages(List.of(
                ProductImage.local(
                        "https://example.com/macbook-1.jpg",
                        "macbook-1.jpg",
                        "macbook-1.jpg",
                        "image/jpeg",
                        100L,
                        0,
                        false
                ),
                ProductImage.local(
                        "https://example.com/macbook-2.jpg",
                        "macbook-2.jpg",
                        "macbook-2.jpg",
                        "image/jpeg",
                        100L,
                        1,
                        true
                )
        ));

        ProductSummaryResponse response = ProductSummaryResponse.from(product);

        assertThat(response.thumbnailUrl()).isEqualTo("https://example.com/macbook-2.jpg");
    }
}
