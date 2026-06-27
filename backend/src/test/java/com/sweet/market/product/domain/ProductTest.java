package com.sweet.market.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

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
    void 상품_이미지는_대표_이미지와_순서를_가진다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        product.replaceImages(List.of(
                ProductImage.local("/uploads/products/public/a.jpg", "a.jpg", "a.jpg", "image/jpeg", 100L, 1, false),
                ProductImage.local("/uploads/products/public/b.jpg", "b.jpg", "b.jpg", "image/jpeg", 100L, 0, true)
        ));

        assertThat(product.getImages()).extracting(ProductImage::getImageUrl)
                .containsExactly("/uploads/products/public/b.jpg", "/uploads/products/public/a.jpg");
        assertThat(product.getImages()).extracting(ProductImage::isRepresentative)
                .containsExactly(true, false);
    }

    @Test
    void 상품_이미지는_최소_한_개가_필요하다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        assertThatThrownBy(() -> product.replaceImages(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product image is required");
    }

    @Test
    void 상품_대표_이미지는_정확히_한_개여야_한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        assertThatThrownBy(() -> product.replaceImages(List.of(
                ProductImage.local("/uploads/products/public/a.jpg", "a.jpg", "a.jpg", "image/jpeg", 100L, 0, false)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product representative image must be exactly one");
    }

    @Test
    void 상품_이미지는_최대_열_개까지_등록할_수_있다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        assertThatThrownBy(() -> product.replaceImages(List.of(
                ProductImage.local("/uploads/products/public/0.jpg", "0.jpg", "0.jpg", "image/jpeg", 100L, 0, true),
                ProductImage.local("/uploads/products/public/1.jpg", "1.jpg", "1.jpg", "image/jpeg", 100L, 1, false),
                ProductImage.local("/uploads/products/public/2.jpg", "2.jpg", "2.jpg", "image/jpeg", 100L, 2, false),
                ProductImage.local("/uploads/products/public/3.jpg", "3.jpg", "3.jpg", "image/jpeg", 100L, 3, false),
                ProductImage.local("/uploads/products/public/4.jpg", "4.jpg", "4.jpg", "image/jpeg", 100L, 4, false),
                ProductImage.local("/uploads/products/public/5.jpg", "5.jpg", "5.jpg", "image/jpeg", 100L, 5, false),
                ProductImage.local("/uploads/products/public/6.jpg", "6.jpg", "6.jpg", "image/jpeg", 100L, 6, false),
                ProductImage.local("/uploads/products/public/7.jpg", "7.jpg", "7.jpg", "image/jpeg", 100L, 7, false),
                ProductImage.local("/uploads/products/public/8.jpg", "8.jpg", "8.jpg", "image/jpeg", 100L, 8, false),
                ProductImage.local("/uploads/products/public/9.jpg", "9.jpg", "9.jpg", "image/jpeg", 100L, 9, false),
                ProductImage.local("/uploads/products/public/10.jpg", "10.jpg", "10.jpg", "image/jpeg", 100L, 10, false)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product image limit exceeded");
    }

    @Test
    void 상품_이미지_순서는_중복될_수_없다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        assertThatThrownBy(() -> product.replaceImages(List.of(
                ProductImage.local("/uploads/products/public/a.jpg", "a.jpg", "a.jpg", "image/jpeg", 100L, 0, true),
                ProductImage.local("/uploads/products/public/b.jpg", "b.jpg", "b.jpg", "image/jpeg", 100L, 0, false)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product image sort order must be unique");
    }

    @Test
    void 저장_파일명이_비어_있으면_로컬_파일이_아니다() {
        ProductImage image = ProductImage.local(
                "/uploads/products/public/a.jpg",
                " ",
                "a.jpg",
                "image/jpeg",
                100L,
                0,
                true
        );

        assertThat(image.isLocalFile()).isFalse();
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
    void 예약_상품을_판매완료로_바꾼다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.reserve();

        product.markSoldOutFromReservation();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    void 예약_상태가_아닌_상품은_판매완료로_바꿀_수_없다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        assertThatThrownBy(product::markSoldOutFromReservation)
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
