package com.sweet.market.coupon;

import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.coupon.api.AvailableCouponCampaignSearchRequest;
import com.sweet.market.coupon.api.MemberCouponSearchRequest;
import com.sweet.market.coupon.application.CouponIssueService;
import com.sweet.market.coupon.domain.*;
import com.sweet.market.coupon.query.CouponDiscoveryQueryService;
import com.sweet.market.coupon.query.CouponWalletQueryService;
import com.sweet.market.coupon.repository.CouponCampaignRepository;
import com.sweet.market.coupon.repository.MemberCouponRepository;
import com.sweet.market.jpalab.QueryOptimizationTestSupport;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CouponQueryOptimizationTest extends QueryOptimizationTestSupport {

    private static final int PAGE_SIZE = 20;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CouponCampaignRepository couponCampaignRepository;

    @Autowired
    private MemberCouponRepository memberCouponRepository;

    @Autowired
    private CouponDiscoveryQueryService couponDiscoveryQueryService;

    @Autowired
    private CouponWalletQueryService couponWalletQueryService;

    @Autowired
    private CouponIssueService couponIssueService;

    @Test
    @Transactional
    void 사용가능_캠페인_한페이지는_선택상품_컬렉션과_카드별_발급조회없이_반환한다() {
        Member member = memberRepository.save(Member.create("available-member@example.com", "encoded-password", "회원"));
        Product target = saveProduct("available-target@example.com", "조회 대상 상품");
        saveActiveSelectedCampaigns(target, PAGE_SIZE + 5);
        flushAndClear();
        resetStatistics();

        var page = couponDiscoveryQueryService.findAvailable(member.getId(),
                new AvailableCouponCampaignSearchRequest(0, PAGE_SIZE, null, null));

        assertThat(page.getContent()).hasSize(PAGE_SIZE);
        assertThat(collectionFetchCount()).isZero();
        assertThat(queryCount()).isLessThanOrEqualTo(2);
    }

    @Test
    @Transactional
    void 사용가능_소진캠페인_한페이지도_카드별_발급조회없이_발급여부와_마감상태를_반환한다() {
        Member member = memberRepository.save(Member.create("available-sold-out-member@example.com", "encoded-password", "회원"));
        Product target = saveProduct("available-sold-out-target@example.com", "소진 조회 대상 상품");
        CouponCampaign campaign = saveActiveLimitedCampaign(target, 1);
        memberCouponRepository.save(MemberCoupon.issue(member, campaign, Instant.now()));
        flushAndClear();
        jdbcTemplate.update("update coupon_campaigns set issued_count = 1 where id = ?", campaign.getId());
        resetStatistics();

        var page = couponDiscoveryQueryService.findAvailable(member.getId(),
                new AvailableCouponCampaignSearchRequest(0, PAGE_SIZE, null, null));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().getFirst().claimed()).isTrue();
        assertThat(page.getContent().getFirst().soldOut()).isTrue();
        assertThat(collectionFetchCount()).isZero();
        assertThat(queryCount()).isLessThanOrEqualTo(2);
    }

    @Test
    @Transactional
    void 쿠폰지갑_한페이지는_캠페인별_N플러스일_조회없이_반환한다() {
        Member member = memberRepository.save(Member.create("wallet-member@example.com", "encoded-password", "회원"));
        Product target = saveProduct("wallet-target@example.com", "지갑 대상 상품");
        Instant issuedAt = Instant.now().minusSeconds(60);
        for (CouponCampaign campaign : saveActiveSelectedCampaigns(target, PAGE_SIZE + 5)) {
            memberCouponRepository.save(MemberCoupon.issue(member, campaign, issuedAt));
        }
        flushAndClear();
        resetStatistics();

        var page = couponWalletQueryService.findMine(member.getId(),
                new MemberCouponSearchRequest(null, 0, PAGE_SIZE));

        assertThat(page.getContent()).hasSize(PAGE_SIZE);
        assertThat(collectionFetchCount()).isZero();
        assertThat(queryCount()).isLessThanOrEqualTo(2);
    }

    @Test
    @Transactional
    void 운영자_캠페인목록_한페이지는_대상상품_컬렉션_N플러스일_조회없이_반환한다() {
        Product target = saveProduct("owner-list-target@example.com", "운영자 목록 대상 상품");
        saveActiveSelectedCampaigns(target, PAGE_SIZE + 5);
        flushAndClear();
        resetStatistics();

        var page = couponCampaignRepository.search(CouponCampaignOwnerType.PLATFORM, null, false, "",
                Instant.EPOCH, Instant.parse("9999-12-31T14:59:59Z"), Instant.now(), PageRequest.of(0, PAGE_SIZE));

        assertThat(page.getContent()).hasSize(PAGE_SIZE);
        assertThat(collectionFetchCount()).isZero();
        assertThat(queryCount()).isLessThanOrEqualTo(2);
    }

    @Test
    void 쿠폰을_발급해도_직접주문과_장바구니체크아웃은_프로모션_가격스냅샷만_생성한다() throws Exception {
        String buyerToken = signupAndLogin("coupon-checkout-buyer@example.com");
        Member buyer = memberRepository.findByEmail("coupon-checkout-buyer@example.com").orElseThrow();
        Product directProduct = saveBusinessProduct("coupon-direct-seller@example.com", "직접 주문 상품");
        Product cartProduct = saveBusinessProduct("coupon-cart-seller@example.com", "장바구니 주문 상품");
        Long couponCampaignId = saveActiveSelectedCampaigns(directProduct, 1).getFirst().getId();
        Long directPromotionId = saveActiveStoreWidePromotion(directProduct);
        Long cartPromotionId = saveActiveStoreWidePromotion(cartProduct);

        assertThat(couponIssueService.claim(buyer.getId(), couponCampaignId).campaignId()).isEqualTo(couponCampaignId);

        Long directOrderId = objectMapper.readTree(mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": %d}".formatted(directProduct.getId())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(post("/api/products/{productId}/cart", cartProduct.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
                .andExpect(status().isOk());
        Long cartItemId = jdbcTemplate.queryForObject(
                "select id from cart_items where buyer_id = ? and product_id = ?", Long.class, buyer.getId(), cartProduct.getId());
        Long cartOrderId = objectMapper.readTree(mockMvc.perform(post("/api/me/cart/checkout")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cartItemIds\": [%d]}".formatted(cartItemId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("orders").get(0).path("id").asLong();

        assertPromotionSnapshot(directOrderId, directPromotionId);
        assertPromotionSnapshot(cartOrderId, cartPromotionId);
        assertThat(jdbcTemplate.queryForObject("select count(*) from member_coupons where member_id = ?", Integer.class, buyer.getId()))
                .isEqualTo(1);
    }

    private List<CouponCampaign> saveActiveSelectedCampaigns(Product target, int count) {
        Instant now = Instant.now();
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> {
                    CouponCampaign campaign = CouponCampaign.create(
                            CouponCampaignOwnerType.PLATFORM, null, CouponScope.SELECTED_PRODUCTS,
                            CouponDiscountType.FIXED_AMOUNT, 1_000L, null, 0L, true,
                            "페이지 쿠폰 " + index, "선택 상품", now.minusSeconds(3_600), now.plusSeconds(86_400),
                            CouponValidityType.DAYS_FROM_ISSUANCE, null, 7, List.of(target));
                    campaign.schedule(now);
                    return couponCampaignRepository.save(campaign);
                })
                .toList();
    }

    private CouponCampaign saveActiveLimitedCampaign(Product target, int issueLimit) {
        Instant now = Instant.now();
        CouponCampaign campaign = CouponCampaign.create(
                CouponCampaignOwnerType.PLATFORM, null, CouponScope.SELECTED_PRODUCTS,
                CouponDiscountType.FIXED_AMOUNT, 1_000L, null, 0L, true,
                "소진 페이지 쿠폰", "선택 상품", now.minusSeconds(3_600), now.plusSeconds(86_400),
                CouponValidityType.DAYS_FROM_ISSUANCE, null, 7, issueLimit, List.of(target));
        campaign.schedule(now);
        return couponCampaignRepository.save(campaign);
    }

    private Product saveProduct(String email, String title) {
        Member seller = memberRepository.save(Member.create(email, "encoded-password", "판매자"));
        return productRepository.save(Product.create(seller, title, "설명", 10_000L));
    }

    private Product saveBusinessProduct(String email, String title) {
        Member seller = memberRepository.save(Member.create(email, "encoded-password", "판매자"));
        Long storeId = jdbcTemplate.queryForObject("""
                insert into stores (version, owner_member_id, type, public_name, introduction, status, created_at, updated_at)
                values (0, ?, 'BUSINESS', ?, '소개', 'ACTIVE', current_timestamp, current_timestamp)
                returning id
                """, Long.class, seller.getId(), title + " 상점");
        Long productId = jdbcTemplate.queryForObject("""
                insert into products (version, store_id, title, description, price, status, sales_policy, category)
                values (0, ?, ?, '설명', 10000, 'ON_SALE', 'SINGLE_ITEM', 'OTHER')
                returning id
                """, Long.class, storeId, title);
        return productRepository.findById(productId).orElseThrow();
    }

    private Long saveActiveStoreWidePromotion(Product product) {
        Long storeId = jdbcTemplate.queryForObject("select store_id from products where id = ?", Long.class, product.getId());
        return jdbcTemplate.queryForObject("""
                insert into promotion_campaigns (
                    version, store_id, scope, discount_type, discount_value, priority, title,
                    start_at, end_at, lifecycle_status, created_at, updated_at
                ) values (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', 1000, 10, 'M25 프로모션',
                    current_timestamp - interval '1 hour', current_timestamp + interval '1 day', 'DRAFT',
                    current_timestamp, current_timestamp)
                returning id
                """, Long.class, storeId);
    }

    private void assertPromotionSnapshot(Long orderId, Long promotionId) {
        assertThat(jdbcTemplate.queryForObject("select promotion_campaign_id from orders where id = ?", Long.class, orderId))
                .isEqualTo(promotionId);
        assertThat(jdbcTemplate.queryForObject("select promotion_discount_amount from orders where id = ?", Long.class, orderId))
                .isEqualTo(1_000L);
        assertThat(jdbcTemplate.queryForObject("select final_price from orders where id = ?", Long.class, orderId))
                .isEqualTo(9_000L);
    }

    private String signupAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, "password123", "구매자"))))
                .andExpect(status().isCreated());
        return objectMapper.readTree(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).path("data").path("accessToken").asText();
    }
}
