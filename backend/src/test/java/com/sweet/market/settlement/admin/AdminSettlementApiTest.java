package com.sweet.market.settlement.admin;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class AdminSettlementApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 관리자는_정산_목록을_필터_없이_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-list@example.com");
        SettlementFixture first = createSettlement("first", LocalDateTime.of(2026, 6, 1, 10, 0));
        SettlementFixture second = createSettlement("second", LocalDateTime.of(2026, 6, 2, 10, 0));

        mockMvc.perform(get("/api/admin/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].settlementId").value(second.settlementId()))
                .andExpect(jsonPath("$.data.content[0].orderId").value(second.orderId()))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(second.sellerId()))
                .andExpect(jsonPath("$.data.content[0].sellerNickname").value("seller-second"))
                .andExpect(jsonPath("$.data.content[0].productId").value(second.productId()))
                .andExpect(jsonPath("$.data.content[0].productTitle").value("MacBook Pro second"))
                .andExpect(jsonPath("$.data.content[0].amount").value(2_000_000))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].settledAt").value("2026-06-02T10:00:00"))
                .andExpect(jsonPath("$.data.content[1].settlementId").value(first.settlementId()))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void 관리자는_주문_ID로_정산을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-order-filter@example.com");
        createSettlement("order-other", LocalDateTime.of(2026, 6, 1, 10, 0));
        SettlementFixture target = createSettlement("order-target", LocalDateTime.of(2026, 6, 2, 10, 0));

        mockMvc.perform(get("/api/admin/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("orderId", target.orderId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].settlementId").value(target.settlementId()))
                .andExpect(jsonPath("$.data.content[0].orderId").value(target.orderId()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_판매자_ID로_정산을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-seller-filter@example.com");
        createSettlement("seller-other", LocalDateTime.of(2026, 6, 1, 10, 0));
        SettlementFixture target = createSettlement("seller-target", LocalDateTime.of(2026, 6, 2, 10, 0));

        mockMvc.perform(get("/api/admin/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("sellerId", target.sellerId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].settlementId").value(target.settlementId()))
                .andExpect(jsonPath("$.data.content[0].sellerId").value(target.sellerId()))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_정산_상태로_정산을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-status-filter@example.com");
        createSettlement("status-completed", LocalDateTime.of(2026, 6, 1, 10, 0));
        SettlementFixture failed = createSettlement("status-failed", LocalDateTime.of(2026, 6, 2, 10, 0));
        updateSettlementStatus(failed.settlementId(), "FAILED");

        mockMvc.perform(get("/api/admin/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].settlementId").value(failed.settlementId()))
                .andExpect(jsonPath("$.data.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_정산일_범위로_정산을_필터링한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-date-filter@example.com");
        createSettlement("date-before", LocalDateTime.of(2026, 5, 31, 23, 59));
        SettlementFixture target = createSettlement("date-target", LocalDateTime.of(2026, 6, 10, 12, 0));
        createSettlement("date-after", LocalDateTime.of(2026, 6, 20, 0, 0));

        mockMvc.perform(get("/api/admin/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("settledFrom", "2026-06-01T00:00:00")
                        .param("settledTo", "2026-06-15T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].settlementId").value(target.settlementId()))
                .andExpect(jsonPath("$.data.content[0].settledAt").value("2026-06-10T12:00:00"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void 관리자는_정산_목록을_페이지로_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-page@example.com");
        SettlementFixture first = createSettlement("page-first", LocalDateTime.of(2026, 6, 1, 10, 0));
        SettlementFixture second = createSettlement("page-second", LocalDateTime.of(2026, 6, 2, 10, 0));
        SettlementFixture third = createSettlement("page-third", LocalDateTime.of(2026, 6, 3, 10, 0));

        mockMvc.perform(get("/api/admin/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].settlementId").value(third.settlementId()))
                .andExpect(jsonPath("$.data.content[1].settlementId").value(second.settlementId()))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.number").value(0));

        mockMvc.perform(get("/api/admin/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].settlementId").value(first.settlementId()))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.number").value(1));
    }

    @Test
    void 관리자는_정산_상세를_조회한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-detail@example.com");
        SettlementFixture settlement = createSettlement("detail", LocalDateTime.of(2026, 6, 5, 14, 30));

        mockMvc.perform(get("/api/admin/settlements/{settlementId}", settlement.settlementId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.settlementId").value(settlement.settlementId()))
                .andExpect(jsonPath("$.data.orderId").value(settlement.orderId()))
                .andExpect(jsonPath("$.data.orderStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.buyerId").value(settlement.buyerId()))
                .andExpect(jsonPath("$.data.buyerNickname").value("buyer-detail"))
                .andExpect(jsonPath("$.data.sellerId").value(settlement.sellerId()))
                .andExpect(jsonPath("$.data.sellerNickname").value("seller-detail"))
                .andExpect(jsonPath("$.data.productId").value(settlement.productId()))
                .andExpect(jsonPath("$.data.productTitle").value("MacBook Pro detail"))
                .andExpect(jsonPath("$.data.amount").value(2_000_000))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.settledAt").value("2026-06-05T14:30:00"));
    }

    @Test
    void 없는_정산_상세는_찾을_수_없다() throws Exception {
        String adminToken = createAdminAndLogin("admin-missing-detail@example.com");

        mockMvc.perform(get("/api/admin/settlements/{settlementId}", 999L)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SETTLEMENT_NOT_FOUND"));
    }

    @Test
    void 일반_회원은_관리자_정산_목록에_접근할_수_없다() throws Exception {
        String memberToken = createMemberAndLogin("member-admin-settlement@example.com");

        mockMvc.perform(get("/api/admin/settlements")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 인증되지_않은_사용자는_관리자_정산_목록에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/admin/settlements"))
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

    private SettlementFixture createSettlement(String suffix, LocalDateTime settledAt) {
        SettlementFixture fixture = transactionTemplate.execute(status -> {
            Member seller = Member.create("seller-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
            Member buyer = Member.create("buyer-" + suffix + "@example.com", "encoded-password", "buyer-" + suffix);
            entityManager.persist(seller);
            entityManager.persist(buyer);

            Product product = Product.create(seller, "MacBook Pro " + suffix, "M3 laptop", 2_000_000L);
            entityManager.persist(product);

            Order order = Order.create(buyer, product);
            order.markPaid();
            order.startShipping();
            order.completeDelivery();
            order.confirm();
            entityManager.persist(order);

            Settlement settlement = Settlement.create(order);
            entityManager.persist(settlement);
            entityManager.flush();

            return new SettlementFixture(
                    settlement.getId(),
                    order.getId(),
                    seller.getId(),
                    buyer.getId(),
                    product.getId()
            );
        });
        updateSettledAt(fixture.settlementId(), settledAt);
        return fixture;
    }

    private void updateSettledAt(Long settlementId, LocalDateTime settledAt) {
        jdbcTemplate.update("update settlements set settled_at = ? where id = ?", settledAt, settlementId);
    }

    private void updateSettlementStatus(Long settlementId, String status) {
        jdbcTemplate.update("update settlements set status = ? where id = ?", status, settlementId);
    }

    private record SettlementFixture(
            Long settlementId,
            Long orderId,
            Long sellerId,
            Long buyerId,
            Long productId
    ) {
    }
}
