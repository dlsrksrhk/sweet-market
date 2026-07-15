package com.sweet.market.order.admin;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.product.domain.Product;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

@TestPropertySource(properties = "spring.batch.job.enabled=false")
class AdminOrderOperationsApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 관리자는_주문_목록을_필터_없이_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-list@example.com");
        OrderFixture first = createOrder("first");
        OrderFixture second = createOrder("second");

        mockMvc.perform(get("/api/admin/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].orderId").value(second.orderId()))
                .andExpect(jsonPath("$.data.content[0].productId").value(second.productId()))
                .andExpect(jsonPath("$.data.content[0].productTitle").value("MacBook Pro second"))
                .andExpect(jsonPath("$.data.content[0].productPrice").value(2_000_000))
                .andExpect(jsonPath("$.data.content[0].buyerId").value(second.buyerId()))
                .andExpect(jsonPath("$.data.content[0].buyerNickname").value("buyer-second"))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(second.sellerId()))
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller-second"))
                .andExpect(jsonPath("$.data.content[0].status").value("CREATED"))
                .andExpect(jsonPath("$.data.content[0].productStatus").value("RESERVED"))
                .andExpect(jsonPath("$.data.content[0].orderedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[1].orderId").value(first.orderId()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void 관리자는_구매자_ID로_주문을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-buyer-filter@example.com");
        createOrder("buyer-other");
        OrderFixture target = createOrder("buyer-target");

        mockMvc.perform(get("/api/admin/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("buyerId", target.buyerId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].orderId").value(target.orderId()))
                .andExpect(jsonPath("$.data.content[0].buyerId").value(target.buyerId()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_판매자_ID로_주문을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-seller-filter@example.com");
        createOrder("seller-other");
        OrderFixture target = createOrder("seller-target");

        mockMvc.perform(get("/api/admin/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("sellerId", target.sellerId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].orderId").value(target.orderId()))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(target.sellerId()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_주문_상태로_주문을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-status-filter@example.com");
        createOrder("status-created");
        OrderFixture paid = createOrder("status-paid", OrderStep.PAID);

        mockMvc.perform(get("/api/admin/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("status", "PAID"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].orderId").value(paid.orderId()))
                .andExpect(jsonPath("$.data.content[0].status").value("PAID"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_상품_ID로_주문을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-product-filter@example.com");
        createOrder("product-other");
        OrderFixture target = createOrder("product-target");

        mockMvc.perform(get("/api/admin/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("productId", target.productId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].orderId").value(target.orderId()))
                .andExpect(jsonPath("$.data.content[0].productId").value(target.productId()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_pageable_정렬로_주문_목록을_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-sort@example.com");
        OrderFixture first = createOrder("sort-first");
        OrderFixture second = createOrder("sort-second");

        mockMvc.perform(get("/api/admin/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("sort", "id,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].orderId").value(first.orderId()))
                .andExpect(jsonPath("$.data.content[1].orderId").value(second.orderId()));
    }

    @Test
    void 관리자는_정산_존재_여부가_포함된_주문_상세를_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-detail@example.com");
        OrderFixture order = createOrder("detail", OrderStep.CONFIRMED_WITH_SETTLEMENT);

        mockMvc.perform(get("/api/admin/orders/{orderId}", order.orderId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(order.orderId()))
                .andExpect(jsonPath("$.data.productId").value(order.productId()))
                .andExpect(jsonPath("$.data.productTitle").value("MacBook Pro detail"))
                .andExpect(jsonPath("$.data.productPrice").value(2_000_000))
                .andExpect(jsonPath("$.data.memberCouponId").isEmpty())
                .andExpect(jsonPath("$.data.couponDiscountAmount").value(0))
                .andExpect(jsonPath("$.data.buyerId").value(order.buyerId()))
                .andExpect(jsonPath("$.data.buyerNickname").value("buyer-detail"))
                .andExpect(jsonPath("$.data.sellerId").value(order.sellerId()))
                .andExpect(jsonPath("$.data.sellerNickname").value("seller-detail"))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.productStatus").value("SOLD_OUT"))
                .andExpect(jsonPath("$.data.orderedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.canceledAt").isEmpty())
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.settlementExists").value(true));
    }

    @Test
    void 관리자_주문_상세는_쿠폰_할인_스냅샷을_반환한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-coupon-detail@example.com");
        OrderFixture order = createOrder("coupon-detail");
        Long couponId = createCouponSnapshot(order.buyerId());
        jdbcTemplate.update("""
                update orders
                set member_coupon_id = ?, coupon_discount_amount = 1_000, final_price = 1_999_000
                where id = ?
                """, couponId, order.orderId());

        mockMvc.perform(get("/api/admin/orders/{orderId}", order.orderId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memberCouponId").value(couponId))
                .andExpect(jsonPath("$.data.couponDiscountAmount").value(1_000L))
                .andExpect(jsonPath("$.data.productPrice").value(1_999_000L));
    }

    @Test
    void 없는_주문_상세는_찾을_수_없다() throws Exception {
        String adminToken = createAdminAndLogin("admin-missing@example.com");

        mockMvc.perform(get("/api/admin/orders/{orderId}", 999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    void 일반_회원은_관리자_주문_목록에_접근할_수_없다() throws Exception {
        String memberToken = createMemberAndLogin("member-admin-order@example.com");

        mockMvc.perform(get("/api/admin/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 인증되지_않은_사용자는_관리자_주문_목록에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private String createAdminAndLogin(String email) throws Exception {
        memberRepository.save(Member.createAdmin(
                email,
                passwordEncoder.encode("password123"),
                "admin"
        ));
        return login(email, "password123");
    }

    private String createMemberAndLogin(String email) throws Exception {
        memberRepository.save(Member.create(
                email,
                passwordEncoder.encode("password123"),
                "member"
        ));
        return login(email, "password123");
    }

    private String login(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new LoginRequest(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("accessToken").asText();
    }

    private OrderFixture createOrder(String suffix) {
        return createOrder(suffix, OrderStep.CREATED);
    }

    private OrderFixture createOrder(String suffix, OrderStep orderStep) {
        return transactionTemplate.execute(status -> {
            Member seller = Member.create("seller-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
            Member buyer = Member.create("buyer-" + suffix + "@example.com", "encoded-password", "buyer-" + suffix);
            entityManager.persist(seller);
            entityManager.persist(buyer);

            Product product = Product.create(seller, "MacBook Pro " + suffix, "M3 laptop", 2_000_000L);
            entityManager.persist(product);

            Order order = Order.create(buyer, product);
            if (orderStep == OrderStep.PAID || orderStep == OrderStep.CONFIRMED_WITH_SETTLEMENT) {
                order.markPaid();
            }
            if (orderStep == OrderStep.CONFIRMED_WITH_SETTLEMENT) {
                order.startShipping();
                order.completeDelivery();
                order.confirm();
            }
            entityManager.persist(order);

            if (orderStep == OrderStep.CONFIRMED_WITH_SETTLEMENT) {
                entityManager.persist(Settlement.create(order));
            }
            entityManager.flush();

            return new OrderFixture(
                    order.getId(),
                    buyer.getId(),
                    seller.getId(),
                    product.getId()
            );
        });
    }

    private Long createCouponSnapshot(Long buyerId) {
        Long campaignId = jdbcTemplate.queryForObject("""
                insert into coupon_campaigns (
                    version, owner_type, scope, discount_type, discount_value, max_discount_amount,
                    minimum_purchase_amount, stackable, title, issue_starts_at, issue_ends_at,
                    validity_type, validity_days, lifecycle_status, issued_count, created_at, updated_at
                ) values (
                    0, 'PLATFORM', 'ALL_PRODUCTS', 'FIXED_AMOUNT', 1_000, null,
                    0, true, '관리자 조회 쿠폰', current_timestamp - interval '1 day', current_timestamp + interval '1 day',
                    'DAYS_FROM_ISSUANCE', 7, 'ENDED', 0, current_timestamp, current_timestamp
                ) returning id
                """, Long.class);
        return jdbcTemplate.queryForObject("""
                insert into member_coupons (
                    member_id, coupon_campaign_id, issued_at, valid_until, discount_type, discount_value,
                    max_discount_amount, minimum_purchase_amount, scope, stackable, status
                ) values (
                    ?, ?, current_timestamp, current_timestamp + interval '7 days', 'FIXED_AMOUNT', 1_000,
                    null, 0, 'ALL_PRODUCTS', true, 'ISSUED'
                ) returning id
                """, Long.class, buyerId, campaignId);
    }

    private enum OrderStep {
        CREATED,
        PAID,
        CONFIRMED_WITH_SETTLEMENT
    }

    private record OrderFixture(
            Long orderId,
            Long buyerId,
            Long sellerId,
            Long productId
    ) {
    }
}
