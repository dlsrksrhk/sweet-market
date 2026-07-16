package com.sweet.market.coupon.domain;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;
import com.sweet.market.store.domain.Store;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CouponCampaignTest {

    private static final Instant ISSUE_STARTS_AT = Instant.parse("2026-07-13T00:00:00Z");
    private static final Instant ISSUE_ENDS_AT = Instant.parse("2026-07-20T00:00:00Z");

    @Test
    void 발급한도는_양수이거나_무제한이어야_한다() {
        assertThatThrownBy(() -> 캠페인(0))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.INVALID_ISSUE_LIMIT);
        assertThat(캠페인(null).getIssueLimit()).isNull();
        assertThat(캠페인(3).remainingIssueCount()).isEqualTo(3);
    }

    @Test
    void 발급한도는_초안에서만_변경할_수_있다() {
        CouponCampaign campaign = 캠페인(3);
        campaign.schedule(Instant.parse("2026-07-15T00:00:00Z"));

        assertThatThrownBy(() -> campaign.changeIssueLimit(5))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.UPDATE_NOT_ALLOWED);
    }

    @Test
    void 발급한도를_초과하면_발급횟수를_증가시킬_수_없다() {
        CouponCampaign campaign = 캠페인(1);
        campaign.recordIssue();

        assertThat(campaign.getIssuedCount()).isEqualTo(1);
        assertThat(campaign.remainingIssueCount()).isZero();
        assertThatThrownBy(campaign::recordIssue)
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(CouponDomainError.ISSUE_LIMIT_EXCEEDED);
        assertThat(campaign.getIssuedCount()).isEqualTo(1);
    }

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
    void 종료된_캠페인의_유효한_미사용_쿠폰은_사용_가능하다() {
        Instant now = Instant.parse("2026-07-14T00:00:00Z");
        CouponCampaign campaign = 발급일_기준_캠페인();
        MemberCoupon coupon = MemberCoupon.issue(회원(), campaign, now);

        campaign.end();

        assertThat(coupon.walletStatus(now)).isEqualTo(MemberCouponStatus.ISSUED);
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

    @Test
    void 발급시작시각이_지난_초안_캠페인은_예약전까지_수정할_수_있다() {
        Instant now = Instant.parse("2026-07-14T00:00:00Z");
        CouponCampaign campaign = CouponCampaign.create(
                CouponCampaignOwnerType.PLATFORM, null, CouponScope.ALL_PRODUCTS,
                CouponDiscountType.FIXED_AMOUNT, 1_000L, null, 0L, false,
                "초안 쿠폰", null, now.minusSeconds(60), now.plusSeconds(3_600),
                CouponValidityType.DAYS_FROM_ISSUANCE, null, 7, List.of());

        campaign.update(CouponScope.ALL_PRODUCTS, CouponDiscountType.FIXED_AMOUNT, 2_000L,
                null, 0L, false, "수정한 초안 쿠폰", null, now.minusSeconds(30), now.plusSeconds(7_200),
                CouponValidityType.DAYS_FROM_ISSUANCE, null, 7, List.of(), now);

        assertThat(campaign.getTitle()).isEqualTo("수정한 초안 쿠폰");
    }

    @Test
    void 선택상품_쿠폰은_발급시점의_대상상품을_스냅샷으로_보존한다() {
        Product first = Product.create(상점("상점"), "첫 상품", "설명", 10_000L);
        Product second = Product.create(상점("상점"), "둘째 상품", "설명", 10_000L);
        ReflectionTestUtils.setField(first, "id", 1L);
        ReflectionTestUtils.setField(second, "id", 2L);
        CouponCampaign campaign = CouponCampaign.create(
                CouponCampaignOwnerType.PLATFORM, null, CouponScope.SELECTED_PRODUCTS,
                CouponDiscountType.FIXED_AMOUNT, 1_000L, null, 0L, false, "선택 쿠폰", null,
                ISSUE_STARTS_AT, ISSUE_ENDS_AT, CouponValidityType.DAYS_FROM_ISSUANCE, null, 7, List.of(first));

        MemberCoupon coupon = MemberCoupon.issue(회원(), campaign, ISSUE_STARTS_AT);
        campaign.update(CouponScope.SELECTED_PRODUCTS, CouponDiscountType.FIXED_AMOUNT, 1_000L,
                null, 0L, false, "변경 쿠폰", null, ISSUE_STARTS_AT, ISSUE_ENDS_AT,
                CouponValidityType.DAYS_FROM_ISSUANCE, null, 7, List.of(second), ISSUE_STARTS_AT.minusSeconds(1));

        assertThat(coupon.getTargetProductIds()).containsExactly(1L);
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

    private CouponCampaign 캠페인(Integer issueLimit) {
        return CouponCampaign.create(
                CouponCampaignOwnerType.PLATFORM, null, CouponScope.ALL_PRODUCTS,
                CouponDiscountType.FIXED_AMOUNT, 1_000L, null, 0L, true,
                "발급 한도 쿠폰", null, ISSUE_STARTS_AT, ISSUE_ENDS_AT,
                CouponValidityType.DAYS_FROM_ISSUANCE, null, 7, issueLimit, List.of());
    }

    private Member 회원() {
        return Member.create("member@example.com", "encoded-password", "회원");
    }

    private Store 상점(String name) {
        return Store.createPersonal(회원(), name, "소개");
    }
}
