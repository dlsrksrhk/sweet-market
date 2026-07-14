package com.sweet.market.promotion.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.promotion.domain.PromotionCampaign;
import com.sweet.market.promotion.domain.PromotionDiscountType;
import com.sweet.market.promotion.domain.PromotionScope;
import com.sweet.market.promotion.repository.PromotionCampaignRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.support.IntegrationTestSupport;

class PromotionPricingServiceTest extends IntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-07-14T00:00:00Z");

    @Autowired
    private PromotionCampaignRepository promotionCampaignRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 선택상품과_상점전체의_정액_정률_할인_중_최종가가_낮은_할인을_선택한다() {
        Product product = 상품을_생성한다(10_000L);
        PromotionCampaign storeWide = 프로모션을_생성한다(product, PromotionScope.STORE_WIDE,
                PromotionDiscountType.PERCENTAGE, 15L, 10, List.of());
        PromotionCampaign selected = 프로모션을_생성한다(product, PromotionScope.SELECTED_PRODUCTS,
                PromotionDiscountType.FIXED_AMOUNT, 2_000L, 10, List.of(product));

        PromotionPrice price = 가격_서비스().quote(product);

        assertThat(price.listPrice()).isEqualTo(10_000L);
        assertThat(price.promotionId()).isEqualTo(selected.getId());
        assertThat(price.promotionDiscountAmount()).isEqualTo(2_000L);
        assertThat(price.effectivePrice()).isEqualTo(8_000L);
        assertThat(storeWide.getId()).isNotEqualTo(price.promotionId());
    }

    @Test
    void 정률_할인은_원단위_내림하고_정액_할인은_가격보다_커도_0원_미만으로_내리지_않는다() {
        Product percentProduct = 상품을_생성한다(9_999L);
        프로모션을_생성한다(percentProduct, PromotionScope.STORE_WIDE,
                PromotionDiscountType.PERCENTAGE, 15L, 10, List.of());
        Product clampProduct = 상품을_생성한다(1_000L);
        프로모션을_생성한다(clampProduct, PromotionScope.STORE_WIDE,
                PromotionDiscountType.FIXED_AMOUNT, 2_000L, 10, List.of());

        PromotionPrice percentPrice = 가격_서비스().quote(percentProduct);
        PromotionPrice clampPrice = 가격_서비스().quote(clampProduct);

        assertThat(percentPrice.promotionDiscountAmount()).isEqualTo(1_499L);
        assertThat(percentPrice.effectivePrice()).isEqualTo(8_500L);
        assertThat(clampPrice.promotionDiscountAmount()).isEqualTo(1_000L);
        assertThat(clampPrice.effectivePrice()).isZero();
    }

    @Test
    void 최종가가_같으면_우선순위가_높고_그래도_같으면_캠페인_ID가_작은_할인을_선택한다() {
        Product product = 상품을_생성한다(10_000L);
        PromotionCampaign lowerPriority = 프로모션을_생성한다(product, PromotionScope.STORE_WIDE,
                PromotionDiscountType.FIXED_AMOUNT, 2_000L, 1, List.of());
        PromotionCampaign higherPriority = 프로모션을_생성한다(product, PromotionScope.STORE_WIDE,
                PromotionDiscountType.FIXED_AMOUNT, 2_000L, 5, List.of());
        Product samePriorityProduct = 상품을_생성한다(10_000L);
        PromotionCampaign first = 프로모션을_생성한다(samePriorityProduct, PromotionScope.STORE_WIDE,
                PromotionDiscountType.FIXED_AMOUNT, 2_000L, 5, List.of());
        프로모션을_생성한다(samePriorityProduct, PromotionScope.STORE_WIDE,
                PromotionDiscountType.FIXED_AMOUNT, 2_000L, 5, List.of());

        PromotionPrice priorityPrice = 가격_서비스().quote(product);
        PromotionPrice idPrice = 가격_서비스().quote(samePriorityProduct);

        assertThat(priorityPrice.promotionId()).isEqualTo(higherPriority.getId());
        assertThat(priorityPrice.promotionId()).isNotEqualTo(lowerPriority.getId());
        assertThat(idPrice.promotionId()).isEqualTo(first.getId());
    }

    @Test
    void 대상이_아니거나_일시정지와_만료된_프로모션은_적용하지_않고_일괄_견적을_반환한다() {
        Product product = 상품을_생성한다(10_000L);
        Product otherProduct = 상품을_생성한다(10_000L);
        프로모션을_생성한다(otherProduct, PromotionScope.SELECTED_PRODUCTS,
                PromotionDiscountType.FIXED_AMOUNT, 2_000L, 10, List.of(otherProduct));
        PromotionCampaign paused = 프로모션을_생성한다(product, PromotionScope.STORE_WIDE,
                PromotionDiscountType.FIXED_AMOUNT, 2_000L, 10, List.of());
        paused.schedule(NOW);
        paused.pause(NOW);
        promotionCampaignRepository.saveAndFlush(paused);
        PromotionCampaign expired = 프로모션을_생성한다(product, PromotionScope.STORE_WIDE,
                PromotionDiscountType.FIXED_AMOUNT, 2_000L, 10, List.of());
        jdbcTemplate.update("update promotion_campaigns set end_at = ? where id = ?",
                java.sql.Timestamp.from(NOW.minusSeconds(1)), expired.getId());

        var prices = 가격_서비스().quoteAll(List.of(product.getId(), otherProduct.getId()));

        assertThat(prices).containsEntry(product.getId(), new PromotionPrice(10_000L, null, null, 0L, 10_000L));
        assertThat(prices.get(otherProduct.getId()).promotionId()).isNotNull();
    }

    private PromotionPricingService 가격_서비스() {
        return new PromotionPricingService(promotionCampaignRepository, productRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Product 상품을_생성한다(long price) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            Member seller = memberRepository.save(Member.create("seller" + System.nanoTime() + "@example.com", "password", "판매자"));
            Store store = Store.applyBusiness(seller, "상점", "소개", "법인", "123-45-67890");
            store.approve();
            return productRepository.save(Product.create(store, "상품", "설명", price));
        });
    }

    private PromotionCampaign 프로모션을_생성한다(
            Product product,
            PromotionScope scope,
            PromotionDiscountType discountType,
            long discountValue,
            int priority,
            List<Product> targets
    ) {
        return promotionCampaignRepository.saveAndFlush(PromotionCampaign.create(
                product.getStore(), scope, discountType, discountValue, priority, "할인", null,
                NOW.minusSeconds(60), NOW.plusSeconds(60), targets
        ));
    }
}
