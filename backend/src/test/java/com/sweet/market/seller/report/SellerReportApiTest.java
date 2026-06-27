package com.sweet.market.seller.report;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
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
class SellerReportApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 판매자는_대시보드_요약을_조회한다() throws Exception {
        String token = createMemberAndLogin("seller-dashboard@example.com", "seller-dashboard");
        Member seller = memberRepository.findAll().get(0);
        Member buyer = saveMember("buyer-dashboard@example.com", "buyer-dashboard");

        saveProduct(seller, "Active", 10_000);
        Product soldOutProduct = saveProduct(seller, "Sold", 20_000);
        Product settledProduct = saveProduct(seller, "Settled", 30_000);

        saveConfirmedOrder(buyer, soldOutProduct, LocalDateTime.now().minusDays(3));
        Order settledOrder = saveConfirmedOrder(buyer, settledProduct, LocalDateTime.now().minusDays(2));
        saveSettlement(settledOrder, LocalDateTime.now().minusDays(1));

        mockMvc.perform(get("/api/seller/reports/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.period.recentDays").value(30))
                .andExpect(jsonPath("$.data.summary.total.activeProductCount").value(1))
                .andExpect(jsonPath("$.data.summary.total.soldOutProductCount").value(2))
                .andExpect(jsonPath("$.data.summary.total.confirmedOrderCount").value(2))
                .andExpect(jsonPath("$.data.summary.total.completedSettlementAmount").value(30_000))
                .andExpect(jsonPath("$.data.summary.total.unsettledConfirmedAmount").value(20_000))
                .andExpect(jsonPath("$.data.summary.recent30Days.orderedCount").value(2))
                .andExpect(jsonPath("$.data.summary.recent30Days.confirmedOrderCount").value(2))
                .andExpect(jsonPath("$.data.summary.recent30Days.completedSettlementAmount").value(30_000))
                .andExpect(jsonPath("$.data.summary.recent30Days.unsettledConfirmedAmount").value(20_000))
                .andExpect(jsonPath("$.data.productStatusCounts[?(@.status == 'ON_SALE')].count").value(hasItem(1)))
                .andExpect(jsonPath("$.data.productStatusCounts[?(@.status == 'SOLD_OUT')].count").value(hasItem(2)))
                .andExpect(jsonPath("$.data.orderStatusCounts[?(@.status == 'CONFIRMED')].count").value(hasItem(2)));
    }

    @Test
    void 판매자_대시보드는_다른_판매자의_데이터를_포함하지_않는다() throws Exception {
        String token = createMemberAndLogin("seller-scope@example.com", "seller-scope");
        Member targetSeller = memberRepository.findAll().get(0);
        Member otherSeller = saveMember("other-seller@example.com", "other-seller");
        Member buyer = saveMember("buyer-scope@example.com", "buyer-scope");

        saveProduct(targetSeller, "Target active", 10_000);
        Order targetOrder = saveConfirmedOrder(buyer, saveProduct(targetSeller, "Target sold", 20_000), LocalDateTime.now().minusDays(1));
        saveSettlement(targetOrder, LocalDateTime.now());

        saveProduct(otherSeller, "Other active", 100_000);
        Order otherOrder = saveConfirmedOrder(buyer, saveProduct(otherSeller, "Other sold", 200_000), LocalDateTime.now().minusDays(1));
        saveSettlement(otherOrder, LocalDateTime.now());

        mockMvc.perform(get("/api/seller/reports/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.total.activeProductCount").value(1))
                .andExpect(jsonPath("$.data.summary.total.soldOutProductCount").value(1))
                .andExpect(jsonPath("$.data.summary.total.confirmedOrderCount").value(1))
                .andExpect(jsonPath("$.data.summary.total.completedSettlementAmount").value(20_000));
    }

    @Test
    void 최근_30일_지표는_이벤트_날짜_기준으로_집계된다() throws Exception {
        String token = createMemberAndLogin("seller-recent@example.com", "seller-recent");
        Member seller = memberRepository.findAll().get(0);
        Member buyer = saveMember("buyer-recent@example.com", "buyer-recent");

        LocalDate today = LocalDate.now();
        LocalDateTime insideWindow = today.minusDays(29).atTime(9, 0);
        LocalDateTime outsideWindow = today.minusDays(30).atTime(23, 59);
        LocalDateTime tomorrowStart = today.plusDays(1).atStartOfDay();

        Product orderedOnlyProduct = saveProduct(seller, "Ordered only", 10_000);
        Product confirmedOnlyProduct = saveProduct(seller, "Confirmed only", 20_000);
        Product settledOnlyProduct = saveProduct(seller, "Settled only", 30_000);
        Product outsideProduct = saveProduct(seller, "Outside", 40_000);
        Product tomorrowProduct = saveProduct(seller, "Tomorrow", 50_000);

        saveConfirmedOrder(buyer, orderedOnlyProduct, insideWindow, outsideWindow);
        saveConfirmedOrder(buyer, confirmedOnlyProduct, outsideWindow, insideWindow);
        Order settledOnlyOrder = saveConfirmedOrder(buyer, settledOnlyProduct, outsideWindow, outsideWindow);
        Order outsideOrder = saveConfirmedOrder(buyer, outsideProduct, outsideWindow, outsideWindow);
        saveConfirmedOrder(buyer, tomorrowProduct, tomorrowStart, tomorrowStart);
        saveSettlement(settledOnlyOrder, insideWindow);
        saveSettlement(outsideOrder, outsideWindow);

        mockMvc.perform(get("/api/seller/reports/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.period.recentFrom").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.data.period.recentTo").value(today.toString()))
                .andExpect(jsonPath("$.data.summary.total.confirmedOrderCount").value(5))
                .andExpect(jsonPath("$.data.summary.total.completedSettlementAmount").value(70_000))
                .andExpect(jsonPath("$.data.summary.total.unsettledConfirmedAmount").value(80_000))
                .andExpect(jsonPath("$.data.summary.recent30Days.orderedCount").value(1))
                .andExpect(jsonPath("$.data.summary.recent30Days.confirmedOrderCount").value(1))
                .andExpect(jsonPath("$.data.summary.recent30Days.completedSettlementAmount").value(30_000))
                .andExpect(jsonPath("$.data.summary.recent30Days.unsettledConfirmedAmount").value(20_000));
    }

    @Test
    void 미정산_확정_금액은_정산이_없는_확정_주문만_합산한다() throws Exception {
        String token = createMemberAndLogin("seller-unsettled@example.com", "seller-unsettled");
        Member seller = memberRepository.findAll().get(0);
        Member buyer = saveMember("buyer-unsettled@example.com", "buyer-unsettled");

        saveConfirmedOrder(buyer, saveProduct(seller, "Unsettled one", 10_000), LocalDateTime.now().minusDays(1));
        saveConfirmedOrder(buyer, saveProduct(seller, "Unsettled two", 20_000), LocalDateTime.now().minusDays(2));
        Order settledOrder = saveConfirmedOrder(buyer, saveProduct(seller, "Settled", 30_000), LocalDateTime.now().minusDays(3));
        saveSettlement(settledOrder, LocalDateTime.now());

        mockMvc.perform(get("/api/seller/reports/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.total.unsettledConfirmedAmount").value(30_000))
                .andExpect(jsonPath("$.data.summary.recent30Days.unsettledConfirmedAmount").value(30_000));
    }

    @Test
    void 완료_정산액은_COMPLETED_정산만_합산한다() throws Exception {
        String token = createMemberAndLogin("seller-completed-settlement@example.com", "seller-completed-settlement");
        Member seller = memberRepository.findAll().get(0);
        Member buyer = saveMember("buyer-completed-settlement@example.com", "buyer-completed-settlement");

        Order completedOrder = saveConfirmedOrder(buyer, saveProduct(seller, "Completed", 10_000), LocalDateTime.now().minusDays(1));
        Order failedOrder = saveConfirmedOrder(buyer, saveProduct(seller, "Failed", 20_000), LocalDateTime.now().minusDays(1));
        saveSettlement(completedOrder, LocalDateTime.now());
        Settlement failedSettlement = saveSettlement(failedOrder, LocalDateTime.now());
        jdbcTemplate.update("update settlements set status = 'FAILED' where id = ?", failedSettlement.getId());

        mockMvc.perform(get("/api/seller/reports/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.total.completedSettlementAmount").value(10_000))
                .andExpect(jsonPath("$.data.summary.recent30Days.completedSettlementAmount").value(10_000));
    }

    @Test
    void 판매_데이터가_없으면_0_지표를_반환한다() throws Exception {
        String token = createMemberAndLogin("seller-empty@example.com", "seller-empty");

        mockMvc.perform(get("/api/seller/reports/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.total.activeProductCount").value(0))
                .andExpect(jsonPath("$.data.summary.total.soldOutProductCount").value(0))
                .andExpect(jsonPath("$.data.summary.total.confirmedOrderCount").value(0))
                .andExpect(jsonPath("$.data.summary.total.completedSettlementAmount").value(0))
                .andExpect(jsonPath("$.data.summary.total.unsettledConfirmedAmount").value(0))
                .andExpect(jsonPath("$.data.summary.recent30Days.orderedCount").value(0))
                .andExpect(jsonPath("$.data.summary.recent30Days.confirmedOrderCount").value(0))
                .andExpect(jsonPath("$.data.summary.recent30Days.completedSettlementAmount").value(0))
                .andExpect(jsonPath("$.data.summary.recent30Days.unsettledConfirmedAmount").value(0));
    }

    @Test
    void 상품과_주문_상태_분포는_모든_상태를_0과_함께_포함한다() throws Exception {
        String token = createMemberAndLogin("seller-statuses@example.com", "seller-statuses");

        mockMvc.perform(get("/api/seller/reports/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productStatusCounts[?(@.status == 'ON_SALE')].count").value(hasItem(0)))
                .andExpect(jsonPath("$.data.productStatusCounts[?(@.status == 'RESERVED')].count").value(hasItem(0)))
                .andExpect(jsonPath("$.data.productStatusCounts[?(@.status == 'SOLD_OUT')].count").value(hasItem(0)))
                .andExpect(jsonPath("$.data.productStatusCounts[?(@.status == 'HIDDEN')].count").value(hasItem(0)))
                .andExpect(jsonPath("$.data.orderStatusCounts[?(@.status == 'CREATED')].count").value(hasItem(0)))
                .andExpect(jsonPath("$.data.orderStatusCounts[?(@.status == 'PAID')].count").value(hasItem(0)))
                .andExpect(jsonPath("$.data.orderStatusCounts[?(@.status == 'SHIPPING')].count").value(hasItem(0)))
                .andExpect(jsonPath("$.data.orderStatusCounts[?(@.status == 'DELIVERED')].count").value(hasItem(0)))
                .andExpect(jsonPath("$.data.orderStatusCounts[?(@.status == 'CONFIRMED')].count").value(hasItem(0)))
                .andExpect(jsonPath("$.data.orderStatusCounts[?(@.status == 'CANCELED')].count").value(hasItem(0)));
    }

    @Test
    void 인증되지_않은_사용자는_판매자_리포트를_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/seller/reports/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void 판매자는_기본_기간_리포트를_조회한다() throws Exception {
        String token = createMemberAndLogin("seller-period-default@example.com", "seller-period-default");

        LocalDate today = LocalDate.now();

        mockMvc.perform(get("/api/seller/reports/period")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.period.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.data.period.to").value(today.toString()))
                .andExpect(jsonPath("$.data.period.days").value(30))
                .andExpect(jsonPath("$.data.summary.orderedCount").value(0))
                .andExpect(jsonPath("$.data.summary.confirmedOrderCount").value(0))
                .andExpect(jsonPath("$.data.summary.confirmedSalesAmount").value(0))
                .andExpect(jsonPath("$.data.summary.completedSettlementAmount").value(0))
                .andExpect(jsonPath("$.data.summary.unsettledConfirmedAmount").value(0))
                .andExpect(jsonPath("$.data.summary.averageConfirmedOrderAmount").value(0))
                .andExpect(jsonPath("$.data.productRankings").isArray())
                .andExpect(jsonPath("$.data.dailySales").isArray())
                .andExpect(jsonPath("$.data.dailySales.length()").value(30))
                .andExpect(jsonPath("$.data.recentSales").isArray())
                .andExpect(jsonPath("$.data.recentSettlements").isArray());
    }

    @Test
    void 기간_리포트는_from과_to를_함께_받아야_한다() throws Exception {
        String token = createMemberAndLogin("seller-period-half@example.com", "seller-period-half");

        mockMvc.perform(get("/api/seller/reports/period")
                        .queryParam("from", LocalDate.now().minusDays(7).toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_PERIOD"));
    }

    @Test
    void 기간_리포트는_시작일이_종료일보다_늦으면_실패한다() throws Exception {
        String token = createMemberAndLogin("seller-period-reversed@example.com", "seller-period-reversed");

        mockMvc.perform(get("/api/seller/reports/period")
                        .queryParam("from", "2026-06-20")
                        .queryParam("to", "2026-06-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_PERIOD"));
    }

    @Test
    void 기간_리포트는_180일을_초과하면_실패한다() throws Exception {
        String token = createMemberAndLogin("seller-period-too-long@example.com", "seller-period-too-long");

        mockMvc.perform(get("/api/seller/reports/period")
                        .queryParam("from", "2026-01-01")
                        .queryParam("to", "2026-07-01")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_PERIOD"));
    }

    @Test
    void 기간_리포트는_ISO_날짜가_아니면_실패한다() throws Exception {
        String token = createMemberAndLogin("seller-period-invalid-date@example.com", "seller-period-invalid-date");

        mockMvc.perform(get("/api/seller/reports/period")
                        .queryParam("from", "2026/06/01")
                        .queryParam("to", "2026-06-30")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REPORT_PERIOD"));
    }

    @Test
    void 인증되지_않은_사용자는_기간_리포트를_조회할_수_없다() throws Exception {
        mockMvc.perform(get("/api/seller/reports/period"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private String createMemberAndLogin(String email, String nickname) throws Exception {
        memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), nickname));
        return login(email, "password123");
    }

    private Member saveMember(String email, String nickname) {
        return memberRepository.save(Member.create(email, passwordEncoder.encode("password123"), nickname));
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

    private Product saveProduct(Member seller, String title, long price) {
        return transactionTemplate.execute(status -> {
            Member managedSeller = entityManager.find(Member.class, seller.getId());
            Product product = Product.create(managedSeller, title, "report fixture", price);
            entityManager.persist(product);
            entityManager.flush();
            return product;
        });
    }

    private Order saveConfirmedOrder(Member buyer, Product product, LocalDateTime eventAt) {
        return saveConfirmedOrder(buyer, product, eventAt, eventAt);
    }

    private Order saveConfirmedOrder(
            Member buyer,
            Product product,
            LocalDateTime orderedAt,
            LocalDateTime confirmedAt
    ) {
        Order order = transactionTemplate.execute(status -> {
            Member managedBuyer = entityManager.find(Member.class, buyer.getId());
            Product managedProduct = entityManager.find(Product.class, product.getId());
            Order createdOrder = Order.create(managedBuyer, managedProduct);
            createdOrder.markPaid();
            createdOrder.startShipping();
            createdOrder.completeDelivery();
            createdOrder.confirm();
            entityManager.persist(createdOrder);
            entityManager.flush();
            return createdOrder;
        });
        jdbcTemplate.update(
                "update orders set ordered_at = ?, confirmed_at = ? where id = ?",
                orderedAt,
                confirmedAt,
                order.getId()
        );
        return order;
    }

    private Settlement saveSettlement(Order order, LocalDateTime settledAt) {
        Settlement settlement = transactionTemplate.execute(status -> {
            Order managedOrder = entityManager.find(Order.class, order.getId());
            Settlement createdSettlement = Settlement.create(managedOrder);
            entityManager.persist(createdSettlement);
            entityManager.flush();
            return createdSettlement;
        });
        jdbcTemplate.update("update settlements set settled_at = ? where id = ?", settledAt, settlement.getId());
        return settlement;
    }
}
