package com.sweet.market.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.member.domain.Member;

class ProductTest {

    @Test
    void createProductWithImages() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");

        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.addImage("https://example.com/macbook-1.jpg");
        product.addImage("https://example.com/macbook-2.jpg");

        assertThat(product.getSeller()).isSameAs(seller);
        assertThat(product.getTitle()).isEqualTo("MacBook Pro");
        assertThat(product.getDescription()).isEqualTo("M3 laptop");
        assertThat(product.getPrice()).isEqualTo(2_000_000L);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product.getImages()).hasSize(2);
        assertThat(product.getImages()).extracting(ProductImage::getImageUrl)
                .containsExactly("https://example.com/macbook-1.jpg", "https://example.com/macbook-2.jpg");
    }

    @Test
    void updateProduct() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        product.update("iPhone", "15 Pro", 1_200_000L);

        assertThat(product.getTitle()).isEqualTo("iPhone");
        assertThat(product.getDescription()).isEqualTo("15 Pro");
        assertThat(product.getPrice()).isEqualTo(1_200_000L);
    }

    @Test
    void hideProduct() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        product.hide();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    void removeImageByIdFailsWhenImageDoesNotExist() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.addImage("https://example.com/macbook-1.jpg");

        assertThatThrownBy(() -> product.removeImage(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product image not found: 999");
    }
}
