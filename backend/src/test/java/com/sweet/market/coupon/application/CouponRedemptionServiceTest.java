package com.sweet.market.coupon.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.coupon.domain.CouponDiscountType;
import com.sweet.market.coupon.domain.CouponScope;
import com.sweet.market.coupon.domain.MemberCoupon;
import com.sweet.market.coupon.domain.MemberCouponStatus;
import com.sweet.market.product.domain.Product;
import com.sweet.market.promotion.application.PromotionPrice;

class CouponRedemptionServiceTest {

    private final CouponRedemptionService service = new CouponRedemptionService(null, null);
    private final Instant now = Instant.parse("2026-07-15T00:00:00Z");

    @Test
    void 프로모션_적용_후_금액에서_정률_쿠폰을_버림_계산한다() {
        MemberCoupon coupon = coupon(CouponDiscountType.PERCENTAGE, 15, 2_000L, 8_000L, true, CouponScope.ALL_PRODUCTS, Set.of());
        Product product = product(101L);

        CouponDiscountQuote quote = service.quote(coupon, product,
                new PromotionPrice(10_000L, 1_001L, "프로모션", 1_000L, 9_000L), now);

        assertThat(quote.memberCouponId()).isEqualTo(7L);
        assertThat(quote.discountAmount()).isEqualTo(1_350L);
        assertThat(quote.finalPrice()).isEqualTo(7_650L);
    }

    @Test
    void 정률_쿠폰은_계산한_할인액을_최대할인금액으로_제한한다() {
        MemberCoupon coupon = coupon(CouponDiscountType.PERCENTAGE, 30, 2_000L, 0L, true, CouponScope.ALL_PRODUCTS, Set.of());

        CouponDiscountQuote quote = service.quote(coupon, product(101L), PromotionPrice.withoutPromotion(10_000L), now);

        assertThat(quote.discountAmount()).isEqualTo(2_000L);
        assertThat(quote.finalPrice()).isEqualTo(8_000L);
    }

    @Test
    void 정액_쿠폰은_기준금액까지만_할인해_0원_주문을_만든다() {
        MemberCoupon coupon = coupon(CouponDiscountType.FIXED_AMOUNT, 20_000L, null, 0L, true, CouponScope.ALL_PRODUCTS, Set.of());

        CouponDiscountQuote quote = service.quote(coupon, product(101L), PromotionPrice.withoutPromotion(10_000L), now);

        assertThat(quote.discountAmount()).isEqualTo(10_000L);
        assertThat(quote.finalPrice()).isZero();
    }

    @Test
    void 대상과_최소금액과_프로모션_중복불가를_검증한다() {
        MemberCoupon targetCoupon = coupon(CouponDiscountType.FIXED_AMOUNT, 1_000L, null, 0L, true, CouponScope.SELECTED_PRODUCTS, Set.of(99L));
        MemberCoupon minimumCoupon = coupon(CouponDiscountType.FIXED_AMOUNT, 1_000L, null, 10_001L, true, CouponScope.ALL_PRODUCTS, Set.of());
        MemberCoupon nonStackableCoupon = coupon(CouponDiscountType.FIXED_AMOUNT, 1_000L, null, 0L, false, CouponScope.ALL_PRODUCTS, Set.of());

        assertError(() -> service.quote(targetCoupon, product(101L), PromotionPrice.withoutPromotion(10_000L), now), ErrorCode.MEMBER_COUPON_TARGET_MISMATCH);
        assertError(() -> service.quote(minimumCoupon, product(101L), PromotionPrice.withoutPromotion(10_000L), now), ErrorCode.MEMBER_COUPON_MINIMUM_PURCHASE_NOT_MET);
        assertError(() -> service.quote(nonStackableCoupon, product(101L), new PromotionPrice(10_000L, 1L, "프로모션", 1_000L, 9_000L), now), ErrorCode.MEMBER_COUPON_PROMOTION_STACKING_NOT_ALLOWED);
    }

    @Test
    void 유효기간이_남은_발급_쿠폰은_캠페인이_종료돼도_견적을_계산한다() {
        MemberCoupon coupon = coupon(CouponDiscountType.FIXED_AMOUNT, 1_000L, null, 0L, true, CouponScope.ALL_PRODUCTS, Set.of());

        CouponDiscountQuote quote = service.quote(coupon, product(101L), PromotionPrice.withoutPromotion(10_000L), now);

        assertThat(quote.finalPrice()).isEqualTo(9_000L);
    }

    private MemberCoupon coupon(CouponDiscountType type, long value, Long maximum, long minimum, boolean stackable,
                                CouponScope scope, Set<Long> targets) {
        MemberCoupon coupon = org.mockito.Mockito.mock(MemberCoupon.class);
        when(coupon.getId()).thenReturn(7L);
        when(coupon.getStatus()).thenReturn(MemberCouponStatus.ISSUED);
        when(coupon.getValidUntil()).thenReturn(now.plusSeconds(60));
        when(coupon.getDiscountType()).thenReturn(type);
        when(coupon.getDiscountValue()).thenReturn(value);
        when(coupon.getMaxDiscountAmount()).thenReturn(maximum);
        when(coupon.getMinimumPurchaseAmount()).thenReturn(minimum);
        when(coupon.isStackable()).thenReturn(stackable);
        when(coupon.getScope()).thenReturn(scope);
        when(coupon.getTargetProductIds()).thenReturn(targets);
        return coupon;
    }

    private Product product(Long id) {
        Product product = org.mockito.Mockito.mock(Product.class);
        when(product.getId()).thenReturn(id);
        return product;
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, ErrorCode errorCode) {
        assertThatThrownBy(action).isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode()).isEqualTo(errorCode);
    }
}
