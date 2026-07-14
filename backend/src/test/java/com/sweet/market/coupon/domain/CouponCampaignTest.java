package com.sweet.market.coupon.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;
import com.sweet.market.store.domain.Store;

class CouponCampaignTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-07-13T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-07-20T00:00:00Z");

    @Test
    void 발급일_기준_유효기간_쿠폰은_발급시각으로_만료시각을_고정한다() {
        Member member = 회원();
        CouponCampaign daysCampaign = 발급일_기준_캠페인();

        MemberCoupon coupon = MemberCoupon.issue(member, daysCampaign, Instant.parse("2026-07-14T00:00:00Z"));

        assertThat(coupon.getValidUntil()).isEqualTo(Instant.parse("2026-07-21T00:00:00Z"));
        assertThat(coupon.getDiscountType()).isEqualTo(CouponDiscountType.FIXED_AMOUNT);
        assertThat(coupon.getDiscountValue()).isEqualTo(1_000L);
        assertThat(coupon.getScope()).isEqualTo(CouponScope.ALL_PRODUCTS);
    }

    @Test
    void 종료된_캠페인의_미사용_쿠폰은_즉시_사용불가다() {
        Instant now = Instant.parse("2026-07-14T00:00:00Z");
        CouponCampaign campaign = 발급일_기준_캠페인();
        MemberCoupon coupon = MemberCoupon.issue(회원(), campaign, now);

        campaign.end();

        assertThat(coupon.walletStatus(now)).isEqualTo(MemberCouponStatus.UNAVAILABLE);
    }

    @Test
    void 상점_쿠폰은_소유_상점_밖의_대상상품을_가질_수_없다() {
        Store store = 상점("상점1");
        Product anotherStoreProduct = Product.create(상점("상점2"), "다른 상점 상품", "설명", 10_000L);

        assertThatThrownBy(() -> CouponCampaign.create(
                CouponCampaignOwnerType.STORE,
                store,
                CouponScope.SELECTED_PRODUCTS,
                CouponDiscountType.FIXED_AMOUNT,
                1_000L,
                null,
                0L,
                false,
                "상점 쿠폰",
                null,
                ISSUE_STARTS_AT,
                ISSUE_ENDS_AT,
                CouponValidityType.COMMON_EXPIRY,
                ISSUE_ENDS_AT,
                null,
                List.of(anotherStoreProduct)
        ))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.TARGET_STORE_MISMATCH);
    }

    @Test
    void 선택상품_쿠폰은_중복되지_않은_대상상품을_요구한다() {
        Product product = Product.create(상점("상점"), "상품", "설명", 10_000L);

        assertThatThrownBy(() -> CouponCampaign.create(
                CouponCampaignOwnerType.PLATFORM,
                null,
                CouponScope.SELECTED_PRODUCTS,
                CouponDiscountType.FIXED_AMOUNT,
                1_000L,
                null,
                0L,
                false,
                "플랫폼 쿠폰",
                null,
                ISSUE_STARTS_AT,
                ISSUE_ENDS_AT,
                CouponValidityType.COMMON_EXPIRY,
                ISSUE_ENDS_AT,
                null,
                List.of(product, product)
        ))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.DUPLICATE_TARGET);
    }

    @Test
    void 정액_쿠폰에는_최대_할인금액을_설정할_수_없다() {
        assertThatThrownBy(() -> CouponCampaign.create(
                CouponCampaignOwnerType.PLATFORM,
                null,
                CouponScope.ALL_PRODUCTS,
                CouponDiscountType.FIXED_AMOUNT,
                1_000L,
                2_000L,
                0L,
                false,
                "플랫폼 쿠폰",
                null,
                ISSUE_STARTS_AT,
                ISSUE_ENDS_AT,
                CouponValidityType.COMMON_EXPIRY,
                ISSUE_ENDS_AT,
                null,
                List.of()
        ))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.MAX_DISCOUNT_AMOUNT_INVALID);
    }

    @Test
    void 정률_쿠폰은_최대_할인금액_없이_생성할_수_있다() {
        CouponCampaign campaign = CouponCampaign.create(
                CouponCampaignOwnerType.PLATFORM,
                null,
                CouponScope.ALL_PRODUCTS,
                CouponDiscountType.PERCENTAGE,
                10L,
                null,
                0L,
                false,
                "정률 쿠폰",
                null,
                ISSUE_STARTS_AT,
                ISSUE_ENDS_AT,
                CouponValidityType.COMMON_EXPIRY,
                ISSUE_ENDS_AT,
                null,
                List.of()
        );

        assertThat(campaign.getMaxDiscountAmount()).isNull();
    }

    @Test
    void 할인값은_0보다_커야_한다() {
        assertThatThrownBy(() -> CouponCampaign.create(
                CouponCampaignOwnerType.PLATFORM,
                null,
                CouponScope.ALL_PRODUCTS,
                CouponDiscountType.FIXED_AMOUNT,
                0L,
                null,
                0L,
                false,
                "플랫폼 쿠폰",
                null,
                ISSUE_STARTS_AT,
                ISSUE_ENDS_AT,
                CouponValidityType.COMMON_EXPIRY,
                ISSUE_ENDS_AT,
                null,
                List.of()
        ))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.INVALID_DISCOUNT_VALUE);
    }

    @Test
    void 선택상품_쿠폰은_서로_다른_객체여도_상품_ID가_중복되면_생성할_수_없다() {
        Product firstProduct = Product.create(상점("상점"), "상품1", "설명", 10_000L);
        Product secondProduct = Product.create(상점("상점"), "상품2", "설명", 10_000L);
        ReflectionTestUtils.setField(firstProduct, "id", 1L);
        ReflectionTestUtils.setField(secondProduct, "id", 1L);

        assertThatThrownBy(() -> CouponCampaign.create(
                CouponCampaignOwnerType.PLATFORM,
                null,
                CouponScope.SELECTED_PRODUCTS,
                CouponDiscountType.FIXED_AMOUNT,
                1_000L,
                null,
                0L,
                false,
                "플랫폼 쿠폰",
                null,
                ISSUE_STARTS_AT,
                ISSUE_ENDS_AT,
                CouponValidityType.COMMON_EXPIRY,
                ISSUE_ENDS_AT,
                null,
                List.of(firstProduct, secondProduct)
        ))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.DUPLICATE_TARGET);
    }

    @Test
    void 공통만료_쿠폰은_발급종료시각_이후에_만료되어야_한다() {
        assertThatThrownBy(() -> CouponCampaign.create(
                CouponCampaignOwnerType.PLATFORM,
                null,
                CouponScope.ALL_PRODUCTS,
                CouponDiscountType.FIXED_AMOUNT,
                1_000L,
                null,
                0L,
                false,
                "플랫폼 쿠폰",
                null,
                ISSUE_STARTS_AT,
                ISSUE_ENDS_AT,
                CouponValidityType.COMMON_EXPIRY,
                ISSUE_ENDS_AT.minusSeconds(1),
                null,
                List.of()
        ))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.INVALID_VALIDITY_POLICY);
    }

    private CouponCampaign 발급일_기준_캠페인() {
        return CouponCampaign.create(
                CouponCampaignOwnerType.PLATFORM,
                null,
                CouponScope.ALL_PRODUCTS,
                CouponDiscountType.FIXED_AMOUNT,
                1_000L,
                null,
                0L,
                true,
                "일주일 쿠폰",
                "신규 회원",
                ISSUE_STARTS_AT,
                ISSUE_ENDS_AT,
                CouponValidityType.DAYS_FROM_ISSUANCE,
                null,
                7,
                List.of()
        );
    }

    private Member 회원() {
        return Member.create("member@example.com", "encoded-password", "회원");
    }

    private Store 상점(String name) {
        return Store.createPersonal(회원(), name, "소개");
    }
}
