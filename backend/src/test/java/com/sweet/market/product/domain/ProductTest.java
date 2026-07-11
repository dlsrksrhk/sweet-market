package com.sweet.market.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.refund.domain.RefundRequest;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.store.domain.Store;

class ProductTest {

    @Test
    void 상품을_이미지와_함께_생성한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");

        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        product.addImage("https://example.com/macbook-1.jpg");
        product.addImage("https://example.com/macbook-2.jpg");

        assertThat(product.getStore().getOwnerMember()).isSameAs(seller);
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
    void 숨김_상품은_다시_판매_중으로_노출할_수_있다() {
        Member seller = Member.create("show@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "상품", "설명", 10_000L);
        product.hide();

        product.show();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 숨김이_아닌_상품은_재노출할_수_없다() {
        Member seller = Member.create("show-conflict@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "상품", "설명", 10_000L);

        assertThatThrownBy(product::show)
                .isInstanceOf(IllegalStateException.class);
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
    void 레거시_이미지_추가는_고유한_순서와_하나의_대표_이미지를_유지한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);

        product.addLegacyImage("https://example.com/macbook-1.jpg");
        product.addLegacyImage("https://example.com/macbook-2.jpg");

        assertThat(product.getImages()).extracting(ProductImage::getSortOrder)
                .containsExactly(0, 1);
        assertThat(product.getImages()).extracting(ProductImage::isRepresentative)
                .containsExactly(true, false);
    }

    @Test
    void 레거시_이미지_추가는_최대_열_개까지만_허용한다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        for (int i = 0; i < 10; i++) {
            product.addLegacyImage("https://example.com/macbook-" + i + ".jpg");
        }

        assertThatThrownBy(() -> product.addLegacyImage("https://example.com/macbook-10.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Product image limit exceeded");
    }

    @Test
    void 레거시_이미지는_삭제_후_추가해도_순서가_중복되지_않는다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        ProductImage first = product.addLegacyImage("https://example.com/macbook-1.jpg");
        ProductImage second = product.addLegacyImage("https://example.com/macbook-2.jpg");
        ProductImage third = product.addLegacyImage("https://example.com/macbook-3.jpg");
        assignImageId(first, 1L);
        assignImageId(second, 2L);
        assignImageId(third, 3L);

        product.removeImage(2L);
        product.addLegacyImage("https://example.com/macbook-4.jpg");

        assertThat(product.getImages()).extracting(ProductImage::getSortOrder)
                .containsExactly(0, 2, 3);
    }

    @Test
    void 대표_이미지를_삭제하면_남은_이미지가_대표_이미지가_된다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        ProductImage first = product.addLegacyImage("https://example.com/macbook-1.jpg");
        ProductImage second = product.addLegacyImage("https://example.com/macbook-2.jpg");
        assignImageId(first, 1L);
        assignImageId(second, 2L);

        product.removeImage(1L);

        assertThat(product.getImages()).extracting(ProductImage::isRepresentative)
                .containsExactly(true);
    }

    @Test
    void 마지막_이미지는_삭제할_수_없다() {
        Member seller = Member.create("seller@example.com", "encoded-password", "seller");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
        ProductImage image = product.addLegacyImage("https://example.com/macbook-1.jpg");
        assignImageId(image, 1L);

        assertThatThrownBy(() -> product.removeImage(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Product image is required");
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

    private void assignImageId(ProductImage image, Long id) {
        ReflectionTestUtils.setField(image, "id", id);
    }

    @Test
    void 상점_운영자는_선택한_활성_상점에_상품을_등록한다() {
        Member owner = Member.create("owner@example.com", "encoded-password", "owner");
        Store store = Store.createPersonal(owner, "선택 상점", "");

        Product product = Product.create(store, "상품", "설명", 10_000L);

        assertThat(product.getStore()).isSameAs(store);
        assertThat(product.isPurchasable()).isTrue();
    }

    @Test
    void 다른_상점_운영자는_상품을_수정할_수_없다() {
        Member owner = Member.create("owner@example.com", "encoded-password", "owner");
        Member other = Member.create("other@example.com", "encoded-password", "other");
        Product product = Product.create(Store.createPersonal(owner, "상점", ""), "상품", "설명", 10_000L);
        ReflectionTestUtils.setField(owner, "id", 1L);
        ReflectionTestUtils.setField(other, "id", 2L);

        assertThat(product.isOwnedBy(other.getId())).isFalse();
    }

    @Test
    void 비활성_사업자_상점에는_상품을_등록할_수_없다_도메인_상태() {
        Store store = Store.applyBusiness(Member.create("owner@example.com", "encoded-password", "owner"), "사업자", "", "법인", "1");

        assertThat(store.getStatus().name()).isEqualTo("PENDING");
    }

    @Test
    void 비활성_사업자_상점_상품은_공개_목록에서_제외된다_도메인_상태() {
        Store store = Store.applyBusiness(Member.create("owner@example.com", "encoded-password", "owner"), "사업자", "", "법인", "1");
        Product product = Product.create(store, "상품", "설명", 10_000L);

        assertThat(product.isPurchasable()).isFalse();
    }

    @Test
    void 비활성_사업자_상점_상품의_직접_조회는_구매_불가를_반환한다_도메인_상태() {
        Store store = Store.applyBusiness(Member.create("owner@example.com", "encoded-password", "owner"), "사업자", "", "법인", "1");

        assertThat(Product.create(store, "상품", "설명", 10_000L).isPurchasable()).isFalse();
    }

    @Test
    void 비활성_사업자_상점_상품은_장바구니에_담거나_주문할_수_없다_도메인_상태() {
        Store store = Store.applyBusiness(Member.create("owner@example.com", "encoded-password", "owner"), "사업자", "", "법인", "1");
        Product product = Product.create(store, "상품", "설명", 10_000L);

        assertThatThrownBy(() -> Order.create(Member.create("buyer@example.com", "encoded-password", "buyer"), product))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 주문은_생성_시점의_상점_소유자를_판매자로_보존한다() {
        Member owner = Member.create("owner@example.com", "encoded-password", "owner");
        Product product = Product.create(Store.createPersonal(owner, "상점", ""), "상품", "설명", 10_000L);

        assertThat(Order.create(Member.create("buyer@example.com", "encoded-password", "buyer"), product).getSeller()).isSameAs(owner);
    }

    @Test
    void 판매자_환불과_정산은_주문_판매자_스냅샷을_사용한다() {
        Member owner = Member.create("owner@example.com", "encoded-password", "owner");
        ReflectionTestUtils.setField(owner, "id", 1L);
        Order order = Order.create(Member.create("buyer@example.com", "encoded-password", "buyer"), Product.create(Store.createPersonal(owner, "상점", ""), "상품", "설명", 10_000L));
        order.markPaid(); order.startShipping(); order.completeDelivery();
        RefundRequest refund = RefundRequest.request(order, order.getBuyer(), "환불 사유는 충분히 깁니다");

        assertThat(refund.isSellerOwnedBy(1L)).isTrue();
        Order settledOrder = Order.create(Member.create("buyer2@example.com", "encoded-password", "buyer2"), Product.create(Store.createPersonal(owner, "상점2", ""), "상품2", "설명", 10_000L));
        settledOrder.markPaid(); settledOrder.startShipping(); settledOrder.completeDelivery(); settledOrder.confirm();
        assertThat(Settlement.create(settledOrder).getSeller()).isSameAs(owner);
    }
}
