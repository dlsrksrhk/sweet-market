package com.sweet.market.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.member.domain.Member;

class ProductTest {

    @Test
    void 상품을_이미지와_함께_생성한다() {
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
    void 상품을_수정한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        product.update("iPhone", "15 Pro", 1_200_000L);

        assertThat(product.getTitle()).isEqualTo("iPhone");
        assertThat(product.getDescription()).isEqualTo("15 Pro");
        assertThat(product.getPrice()).isEqualTo(1_200_000L);
    }

    @Test
    void 상품을_숨김_처리한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        product.hide();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.HIDDEN);
    }

    @Test
    void 존재하지_않는_이미지_ID로_삭제하면_실패한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.addImage("https://example.com/macbook-1.jpg");

        assertThatThrownBy(() -> product.removeImage(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product image not found: 999");
    }

    @Test
    void 판매중_상품을_예약한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        product.reserve();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.RESERVED);
    }

    @Test
    void 예약_상품을_판매중으로_복구한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.reserve();

        product.restoreOnSaleFromReservation();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 판매중이_아닌_상품은_예약할_수_없다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.hide();

        assertThatThrownBy(product::reserve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Product is not on sale: HIDDEN");
    }

    @Test
    void 예약_상태가_아닌_상품은_판매중으로_복구할_수_없다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        assertThatThrownBy(product::restoreOnSaleFromReservation)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Product is not reserved: ON_SALE");
    }

    @Test
    void 예약_상품은_숨김_처리할_수_없다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.reserve();

        assertThatThrownBy(product::hide)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Reserved product cannot be changed");
    }

    @Test
    void 예약_상품은_수정할_수_없다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.reserve();

        assertThatThrownBy(() -> product.update("iPhone", "15 Pro", 1_200_000L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Reserved product cannot be changed");
    }
}
