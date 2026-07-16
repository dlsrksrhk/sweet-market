package com.sweet.market.promotion.domain;

import com.sweet.market.common.domain.error.DomainException;
import com.sweet.market.member.domain.Member;
import com.sweet.market.product.domain.Product;
import com.sweet.market.store.domain.Store;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionCampaignTest {

    private static final Instant START_AT = Instant.parse("2026-07-14T00:00:00Z");
    private static final Instant END_AT = Instant.parse("2026-07-15T00:00:00Z");

    @Test
    void 예약_프로모션은_시작시각부터_활성으로_판단한다() {
        PromotionCampaign campaign = 캠페인(START_AT, END_AT);
        campaign.schedule(Instant.parse("2026-07-13T00:00:00Z"));

        assertThat(campaign.effectiveStatus(Instant.parse("2026-07-13T23:59:59Z")))
                .isEqualTo(PromotionEffectiveStatus.SCHEDULED);
        assertThat(campaign.effectiveStatus(START_AT)).isEqualTo(PromotionEffectiveStatus.ACTIVE);
        assertThat(campaign.effectiveStatus(END_AT)).isEqualTo(PromotionEffectiveStatus.ENDED);
    }

    @Test
    void 일시중지한_프로모션은_즉시_일시중지로_판단한다() {
        PromotionCampaign campaign = 캠페인(START_AT, END_AT);
        campaign.schedule(Instant.parse("2026-07-13T00:00:00Z"));

        campaign.pause(START_AT);

        assertThat(campaign.effectiveStatus(START_AT)).isEqualTo(PromotionEffectiveStatus.PAUSED);
    }

    @Test
    void 종료전_프로모션을_재개하면_예약상태로_돌아간다() {
        PromotionCampaign campaign = 캠페인(START_AT, END_AT);
        campaign.schedule(Instant.parse("2026-07-13T00:00:00Z"));
        campaign.pause(START_AT);

        campaign.resume(Instant.parse("2026-07-14T12:00:00Z"));

        assertThat(campaign.getLifecycleStatus()).isEqualTo(PromotionLifecycleStatus.SCHEDULED);
        assertThat(campaign.effectiveStatus(Instant.parse("2026-07-14T12:00:00Z")))
                .isEqualTo(PromotionEffectiveStatus.ACTIVE);
    }

    @Test
    void 종료된_프로모션은_재개할_수_없다() {
        PromotionCampaign campaign = 캠페인(START_AT, END_AT);
        campaign.end();

        assertThatThrownBy(() -> campaign.resume(Instant.parse("2026-07-14T00:00:00Z")))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(PromotionDomainError.LIFECYCLE_TRANSITION_NOT_ALLOWED);
    }

    @Test
    void 활성_프로모션은_전체_수정할_수_없다() {
        PromotionCampaign campaign = 캠페인(START_AT, END_AT);
        campaign.schedule(Instant.parse("2026-07-13T00:00:00Z"));

        assertThatThrownBy(() -> campaign.update(
                PromotionScope.STORE_WIDE,
                PromotionDiscountType.FIXED_AMOUNT,
                2_000L,
                10,
                "수정 할인",
                null,
                START_AT,
                END_AT,
                List.of(),
                START_AT
        ))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(PromotionDomainError.UPDATE_NOT_ALLOWED);
    }

    @Test
    void 시작시각이_지난_초안_프로모션은_전체_수정할_수_없다() {
        PromotionCampaign campaign = 캠페인(START_AT, END_AT);

        assertThatThrownBy(() -> campaign.update(
                PromotionScope.STORE_WIDE,
                PromotionDiscountType.FIXED_AMOUNT,
                2_000L,
                10,
                "수정 할인",
                null,
                START_AT,
                END_AT,
                List.of(),
                START_AT
        ))
                .isInstanceOf(DomainException.class)
                .extracting(exception -> ((DomainException) exception).error())
                .isEqualTo(PromotionDomainError.UPDATE_NOT_ALLOWED);
    }

    @Test
    void 선택상품_프로모션은_대상상품을_보관하고_상점전체_프로모션은_대상이_없다() {
        Store store = 상점();
        Product product = Product.create(store, "상품", "설명", 10_000L);

        PromotionCampaign selected = PromotionCampaign.create(
                store,
                PromotionScope.SELECTED_PRODUCTS,
                PromotionDiscountType.PERCENTAGE,
                10L,
                1,
                "선택 할인",
                "라벨",
                START_AT,
                END_AT,
                List.of(product)
        );
        PromotionCampaign storeWide = PromotionCampaign.create(
                store,
                PromotionScope.STORE_WIDE,
                PromotionDiscountType.FIXED_AMOUNT,
                1_000L,
                1,
                "전체 할인",
                null,
                START_AT,
                END_AT,
                List.of()
        );

        assertThat(selected.getTargets()).extracting(target -> target.getProduct()).containsExactly(product);
        assertThat(storeWide.getTargets()).isEmpty();
    }

    private PromotionCampaign 캠페인(Instant startAt, Instant endAt) {
        return PromotionCampaign.create(
                상점(),
                PromotionScope.STORE_WIDE,
                PromotionDiscountType.FIXED_AMOUNT,
                1_000L,
                1,
                "여름 할인",
                null,
                startAt,
                endAt,
                List.of()
        );
    }

    private Store 상점() {
        Member owner = Member.create("owner@example.com", "encoded-password", "owner");
        return Store.createPersonal(owner, "상점", "소개");
    }
}
