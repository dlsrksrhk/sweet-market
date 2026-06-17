# Milestone 12 Seller Reports Dashboard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a seller-facing `/me/reports` dashboard backed by `GET /api/seller/reports/dashboard`.

**Architecture:** Add one authenticated seller report API that derives the seller id from the JWT principal and returns one dashboard DTO containing all-time metrics, recent 30-day metrics, and status distributions. Backend aggregation stays in repository projection queries and a small `seller.report` query service; frontend work adds one API client, one route, one page, and a nav link.

**Tech Stack:** Spring Boot, Spring MVC, Spring Security, Spring Data JPA, Hibernate, PostgreSQL/Testcontainers, JUnit 5, MockMvc, React, TypeScript, Vite, TanStack Query.

---

## File Structure

Create backend files:

- `backend/src/main/java/com/sweet/market/seller/report/SellerReportController.java`: authenticated REST endpoint for seller report dashboard.
- `backend/src/main/java/com/sweet/market/seller/report/SellerReportQueryService.java`: orchestrates aggregate queries, recent 30-day boundaries, zero normalization, and status expansion.
- `backend/src/main/java/com/sweet/market/seller/report/SellerDashboardReportResponse.java`: top-level dashboard response.
- `backend/src/main/java/com/sweet/market/seller/report/SellerReportPeriodResponse.java`: recent period metadata.
- `backend/src/main/java/com/sweet/market/seller/report/SellerReportSummaryResponse.java`: wraps total and recent summaries.
- `backend/src/main/java/com/sweet/market/seller/report/SellerReportTotalSummaryResponse.java`: all-time summary metrics.
- `backend/src/main/java/com/sweet/market/seller/report/SellerReportRecentSummaryResponse.java`: recent 30-day summary metrics.
- `backend/src/main/java/com/sweet/market/seller/report/SellerStatusCountResponse.java`: status/count response used by product and order distributions.
- `backend/src/main/java/com/sweet/market/seller/report/SellerProductStatusCountProjection.java`: repository projection for product status counts.
- `backend/src/main/java/com/sweet/market/seller/report/SellerOrderStatusCountProjection.java`: repository projection for order status counts.

Modify backend files:

- `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`: add seller-scoped product count and status distribution aggregate methods.
- `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`: add seller-scoped order count, status distribution, and unsettled amount aggregate methods.
- `backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java`: add seller-scoped completed settlement amount aggregate methods.

Create backend test file:

- `backend/src/test/java/com/sweet/market/seller/report/SellerReportApiTest.java`: MockMvc integration coverage for seller scoping, zero values, recent 30-day boundaries, status expansion, and anonymous access.

Create web files:

- `web/src/features/reports/sellerReportApi.ts`: TypeScript response types and API client.
- `web/src/pages/MyReportsPage.tsx`: seller reports dashboard page.

Modify web files:

- `web/src/app/router.tsx`: add authenticated `/me/reports` route.
- `web/src/shared/layout/Shell.tsx`: add logged-in navigation link.
- `web/src/shared/styles.css`: add compact dashboard/status distribution styles.

Do not modify or stage `backend/src/main/resources/application.yaml`.

---

### Task 1: Backend API Test

**Files:**
- Create: `backend/src/test/java/com/sweet/market/seller/report/SellerReportApiTest.java`

- [ ] **Step 1: Create the seller report API integration test**

Create `backend/src/test/java/com/sweet/market/seller/report/SellerReportApiTest.java` with this content:

```java
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

        Product activeProduct = saveProduct(seller, "Active", 10_000);
        Product soldOutProduct = saveProduct(seller, "Sold", 20_000);
        Product settledProduct = saveProduct(seller, "Settled", 30_000);

        Order unsettledOrder = saveConfirmedOrder(buyer, soldOutProduct, LocalDateTime.now().minusDays(3));
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

        Product insideProduct = saveProduct(seller, "Inside", 30_000);
        Product outsideProduct = saveProduct(seller, "Outside", 40_000);

        Order insideOrder = saveConfirmedOrder(buyer, insideProduct, insideWindow);
        Order outsideOrder = saveConfirmedOrder(buyer, outsideProduct, outsideWindow);
        saveSettlement(insideOrder, insideWindow.plusHours(1));
        saveSettlement(outsideOrder, outsideWindow.plusHours(1));

        mockMvc.perform(get("/api/seller/reports/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.total.confirmedOrderCount").value(2))
                .andExpect(jsonPath("$.data.summary.total.completedSettlementAmount").value(70_000))
                .andExpect(jsonPath("$.data.summary.recent30Days.orderedCount").value(1))
                .andExpect(jsonPath("$.data.summary.recent30Days.confirmedOrderCount").value(1))
                .andExpect(jsonPath("$.data.summary.recent30Days.completedSettlementAmount").value(30_000));
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
                eventAt,
                eventAt,
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
```

- [ ] **Step 2: Run the focused backend test and verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.seller.report.SellerReportApiTest"
```

Expected: FAIL because `/api/seller/reports/dashboard` is not implemented yet and the first authenticated dashboard request returns 404 instead of 200.

---

### Task 2: Backend Report API Implementation

**Files:**
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerReportController.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerReportQueryService.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerDashboardReportResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerReportPeriodResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerReportSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerReportTotalSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerReportRecentSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerStatusCountResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerProductStatusCountProjection.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerOrderStatusCountProjection.java`
- Modify: `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java`

- [ ] **Step 1: Add response and projection records/interfaces**

Create `backend/src/main/java/com/sweet/market/seller/report/SellerDashboardReportResponse.java`:

```java
package com.sweet.market.seller.report;

import java.time.LocalDateTime;
import java.util.List;

public record SellerDashboardReportResponse(
        LocalDateTime generatedAt,
        SellerReportPeriodResponse period,
        SellerReportSummaryResponse summary,
        List<SellerStatusCountResponse> productStatusCounts,
        List<SellerStatusCountResponse> orderStatusCounts
) {
}
```

Create `backend/src/main/java/com/sweet/market/seller/report/SellerReportPeriodResponse.java`:

```java
package com.sweet.market.seller.report;

import java.time.LocalDate;

public record SellerReportPeriodResponse(
        int recentDays,
        LocalDate recentFrom,
        LocalDate recentTo
) {
}
```

Create `backend/src/main/java/com/sweet/market/seller/report/SellerReportSummaryResponse.java`:

```java
package com.sweet.market.seller.report;

public record SellerReportSummaryResponse(
        SellerReportTotalSummaryResponse total,
        SellerReportRecentSummaryResponse recent30Days
) {
}
```

Create `backend/src/main/java/com/sweet/market/seller/report/SellerReportTotalSummaryResponse.java`:

```java
package com.sweet.market.seller.report;

public record SellerReportTotalSummaryResponse(
        long activeProductCount,
        long soldOutProductCount,
        long confirmedOrderCount,
        long completedSettlementAmount,
        long unsettledConfirmedAmount
) {
}
```

Create `backend/src/main/java/com/sweet/market/seller/report/SellerReportRecentSummaryResponse.java`:

```java
package com.sweet.market.seller.report;

public record SellerReportRecentSummaryResponse(
        long orderedCount,
        long confirmedOrderCount,
        long completedSettlementAmount,
        long unsettledConfirmedAmount
) {
}
```

Create `backend/src/main/java/com/sweet/market/seller/report/SellerStatusCountResponse.java`:

```java
package com.sweet.market.seller.report;

public record SellerStatusCountResponse(
        String status,
        long count
) {
}
```

Create `backend/src/main/java/com/sweet/market/seller/report/SellerProductStatusCountProjection.java`:

```java
package com.sweet.market.seller.report;

import com.sweet.market.product.domain.ProductStatus;

public interface SellerProductStatusCountProjection {

    ProductStatus getStatus();

    long getCount();
}
```

Create `backend/src/main/java/com/sweet/market/seller/report/SellerOrderStatusCountProjection.java`:

```java
package com.sweet.market.seller.report;

import com.sweet.market.order.domain.OrderStatus;

public interface SellerOrderStatusCountProjection {

    OrderStatus getStatus();

    long getCount();
}
```

- [ ] **Step 2: Add product aggregate repository methods**

Modify `backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java`.

Add imports:

```java
import java.util.List;

import com.sweet.market.seller.report.SellerProductStatusCountProjection;
```

Add these methods near the existing seller/admin query methods:

```java
long countBySellerIdAndStatus(Long sellerId, ProductStatus status);

@Query("""
        select p.status as status, count(p) as count
        from Product p
        where p.seller.id = :sellerId
        group by p.status
        """)
List<SellerProductStatusCountProjection> countProductStatusesBySellerId(@Param("sellerId") Long sellerId);
```

- [ ] **Step 3: Add order aggregate repository methods**

Modify `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`.

Add imports:

```java
import java.time.LocalDateTime;
import java.util.List;

import com.sweet.market.seller.report.SellerOrderStatusCountProjection;
```

Add these methods near the existing admin/order count methods:

```java
@Query("""
        select count(o)
        from Order o
        join o.product p
        where p.seller.id = :sellerId
          and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
        """)
long countConfirmedOrdersBySellerId(@Param("sellerId") Long sellerId);

@Query("""
        select count(o)
        from Order o
        join o.product p
        where p.seller.id = :sellerId
          and o.orderedAt >= :fromInclusive
          and o.orderedAt < :toExclusive
        """)
long countOrdersBySellerIdAndOrderedAtBetween(
        @Param("sellerId") Long sellerId,
        @Param("fromInclusive") LocalDateTime fromInclusive,
        @Param("toExclusive") LocalDateTime toExclusive
);

@Query("""
        select count(o)
        from Order o
        join o.product p
        where p.seller.id = :sellerId
          and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
          and o.confirmedAt >= :fromInclusive
          and o.confirmedAt < :toExclusive
        """)
long countConfirmedOrdersBySellerIdAndConfirmedAtBetween(
        @Param("sellerId") Long sellerId,
        @Param("fromInclusive") LocalDateTime fromInclusive,
        @Param("toExclusive") LocalDateTime toExclusive
);

@Query("""
        select o.status as status, count(o) as count
        from Order o
        join o.product p
        where p.seller.id = :sellerId
        group by o.status
        """)
List<SellerOrderStatusCountProjection> countOrderStatusesBySellerId(@Param("sellerId") Long sellerId);

@Query("""
        select coalesce(sum(p.price), 0)
        from Order o
        join o.product p
        where p.seller.id = :sellerId
          and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
          and not exists (
              select 1
              from Settlement s
              where s.order = o
          )
        """)
Long sumUnsettledConfirmedAmountBySellerId(@Param("sellerId") Long sellerId);

@Query("""
        select coalesce(sum(p.price), 0)
        from Order o
        join o.product p
        where p.seller.id = :sellerId
          and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
          and o.confirmedAt >= :fromInclusive
          and o.confirmedAt < :toExclusive
          and not exists (
              select 1
              from Settlement s
              where s.order = o
          )
        """)
Long sumUnsettledConfirmedAmountBySellerIdAndConfirmedAtBetween(
        @Param("sellerId") Long sellerId,
        @Param("fromInclusive") LocalDateTime fromInclusive,
        @Param("toExclusive") LocalDateTime toExclusive
);
```

- [ ] **Step 4: Add settlement aggregate repository methods**

Modify `backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java`.

Add these methods near the seller/admin query methods:

```java
@Query("""
        select coalesce(sum(s.amount), 0)
        from Settlement s
        where s.seller.id = :sellerId
          and s.status = com.sweet.market.settlement.domain.SettlementStatus.COMPLETED
        """)
Long sumCompletedAmountBySellerId(@Param("sellerId") Long sellerId);

@Query("""
        select coalesce(sum(s.amount), 0)
        from Settlement s
        where s.seller.id = :sellerId
          and s.status = com.sweet.market.settlement.domain.SettlementStatus.COMPLETED
          and s.settledAt >= :fromInclusive
          and s.settledAt < :toExclusive
        """)
Long sumCompletedAmountBySellerIdAndSettledAtBetween(
        @Param("sellerId") Long sellerId,
        @Param("fromInclusive") LocalDateTime fromInclusive,
        @Param("toExclusive") LocalDateTime toExclusive
);
```

- [ ] **Step 5: Add query service**

Create `backend/src/main/java/com/sweet/market/seller/report/SellerReportQueryService.java`:

```java
package com.sweet.market.seller.report;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.settlement.repository.SettlementRepository;

@Service
@Transactional(readOnly = true)
public class SellerReportQueryService {

    private static final int RECENT_DAYS = 30;

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final SettlementRepository settlementRepository;

    public SellerReportQueryService(
            ProductRepository productRepository,
            OrderRepository orderRepository,
            SettlementRepository settlementRepository
    ) {
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.settlementRepository = settlementRepository;
    }

    public SellerDashboardReportResponse getDashboard(Long sellerId) {
        LocalDate recentTo = LocalDate.now();
        LocalDate recentFrom = recentTo.minusDays(RECENT_DAYS - 1L);
        LocalDateTime fromInclusive = recentFrom.atStartOfDay();
        LocalDateTime toExclusive = recentTo.plusDays(1).atStartOfDay();

        SellerReportTotalSummaryResponse total = new SellerReportTotalSummaryResponse(
                productRepository.countBySellerIdAndStatus(sellerId, ProductStatus.ON_SALE),
                productRepository.countBySellerIdAndStatus(sellerId, ProductStatus.SOLD_OUT),
                orderRepository.countConfirmedOrdersBySellerId(sellerId),
                zeroIfNull(settlementRepository.sumCompletedAmountBySellerId(sellerId)),
                zeroIfNull(orderRepository.sumUnsettledConfirmedAmountBySellerId(sellerId))
        );

        SellerReportRecentSummaryResponse recent = new SellerReportRecentSummaryResponse(
                orderRepository.countOrdersBySellerIdAndOrderedAtBetween(sellerId, fromInclusive, toExclusive),
                orderRepository.countConfirmedOrdersBySellerIdAndConfirmedAtBetween(sellerId, fromInclusive, toExclusive),
                zeroIfNull(settlementRepository.sumCompletedAmountBySellerIdAndSettledAtBetween(
                        sellerId,
                        fromInclusive,
                        toExclusive
                )),
                zeroIfNull(orderRepository.sumUnsettledConfirmedAmountBySellerIdAndConfirmedAtBetween(
                        sellerId,
                        fromInclusive,
                        toExclusive
                ))
        );

        return new SellerDashboardReportResponse(
                LocalDateTime.now(),
                new SellerReportPeriodResponse(RECENT_DAYS, recentFrom, recentTo),
                new SellerReportSummaryResponse(total, recent),
                expandProductStatusCounts(productRepository.countProductStatusesBySellerId(sellerId)),
                expandOrderStatusCounts(orderRepository.countOrderStatusesBySellerId(sellerId))
        );
    }

    private List<SellerStatusCountResponse> expandProductStatusCounts(List<SellerProductStatusCountProjection> rows) {
        Map<ProductStatus, SellerProductStatusCountProjection> rowByStatus = rows.stream()
                .collect(Collectors.toMap(SellerProductStatusCountProjection::getStatus, Function.identity()));

        return Arrays.stream(ProductStatus.values())
                .map(status -> new SellerStatusCountResponse(
                        status.name(),
                        rowByStatus.getOrDefault(status, new ProductStatusCount(status, 0L)).getCount()
                ))
                .toList();
    }

    private List<SellerStatusCountResponse> expandOrderStatusCounts(List<SellerOrderStatusCountProjection> rows) {
        Map<OrderStatus, SellerOrderStatusCountProjection> rowByStatus = rows.stream()
                .collect(Collectors.toMap(SellerOrderStatusCountProjection::getStatus, Function.identity()));

        return Arrays.stream(OrderStatus.values())
                .map(status -> new SellerStatusCountResponse(
                        status.name(),
                        rowByStatus.getOrDefault(status, new OrderStatusCount(status, 0L)).getCount()
                ))
                .toList();
    }

    private long zeroIfNull(Long value) {
        if (value == null) {
            return 0L;
        }
        return value;
    }

    private record ProductStatusCount(ProductStatus status, long count) implements SellerProductStatusCountProjection {

        @Override
        public ProductStatus getStatus() {
            return status;
        }

        @Override
        public long getCount() {
            return count;
        }
    }

    private record OrderStatusCount(OrderStatus status, long count) implements SellerOrderStatusCountProjection {

        @Override
        public OrderStatus getStatus() {
            return status;
        }

        @Override
        public long getCount() {
            return count;
        }
    }
}
```

- [ ] **Step 6: Add controller**

Create `backend/src/main/java/com/sweet/market/seller/report/SellerReportController.java`:

```java
package com.sweet.market.seller.report;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;

@RestController
@RequestMapping("/api/seller/reports")
public class SellerReportController {

    private final SellerReportQueryService sellerReportQueryService;

    public SellerReportController(SellerReportQueryService sellerReportQueryService) {
        this.sellerReportQueryService = sellerReportQueryService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<SellerDashboardReportResponse> dashboard(Authentication authentication) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(sellerReportQueryService.getDashboard(member.id()));
    }
}
```

- [ ] **Step 7: Run focused backend test**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.seller.report.SellerReportApiTest"
```

Expected: PASS.

- [ ] **Step 8: Run backend test suite**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: PASS.

- [ ] **Step 9: Commit backend report API**

Run:

```powershell
git status --short
git add -- backend/src/main/java/com/sweet/market/seller/report backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java backend/src/test/java/com/sweet/market/seller/report/SellerReportApiTest.java
git commit -m "feat: add seller reports dashboard api"
```

Expected: commit succeeds. `backend/src/main/resources/application.yaml` remains unstaged.

---

### Task 3: Web Reports Dashboard

**Files:**
- Create: `web/src/features/reports/sellerReportApi.ts`
- Create: `web/src/pages/MyReportsPage.tsx`
- Modify: `web/src/app/router.tsx`
- Modify: `web/src/shared/layout/Shell.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Add seller report API client**

Create `web/src/features/reports/sellerReportApi.ts`:

```ts
import { api } from '../../shared/api/http';

export type ProductStatus = 'ON_SALE' | 'RESERVED' | 'SOLD_OUT' | 'HIDDEN';
export type OrderStatus = 'CREATED' | 'PAID' | 'SHIPPING' | 'DELIVERED' | 'CONFIRMED' | 'CANCELED';

export type SellerReportPeriod = {
  recentDays: number;
  recentFrom: string;
  recentTo: string;
};

export type SellerReportTotalSummary = {
  activeProductCount: number;
  soldOutProductCount: number;
  confirmedOrderCount: number;
  completedSettlementAmount: number;
  unsettledConfirmedAmount: number;
};

export type SellerReportRecentSummary = {
  orderedCount: number;
  confirmedOrderCount: number;
  completedSettlementAmount: number;
  unsettledConfirmedAmount: number;
};

export type SellerReportSummary = {
  total: SellerReportTotalSummary;
  recent30Days: SellerReportRecentSummary;
};

export type SellerStatusCount = {
  status: ProductStatus | OrderStatus;
  count: number;
};

export type SellerDashboardReport = {
  generatedAt: string;
  period: SellerReportPeriod;
  summary: SellerReportSummary;
  productStatusCounts: SellerStatusCount[];
  orderStatusCounts: SellerStatusCount[];
};

export function getSellerDashboardReport() {
  return api<SellerDashboardReport>('/api/seller/reports/dashboard');
}
```

- [ ] **Step 2: Add reports page**

Create `web/src/pages/MyReportsPage.tsx`:

```tsx
import { useQuery } from '@tanstack/react-query';
import { getSellerDashboardReport, type SellerStatusCount } from '../features/reports/sellerReportApi';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';

const currencyFormatter = new Intl.NumberFormat('ko-KR');
const numberFormatter = new Intl.NumberFormat('ko-KR');
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

export function MyReportsPage() {
  const { data, error, isLoading } = useQuery({
    queryKey: ['seller-dashboard-report'],
    queryFn: getSellerDashboardReport,
  });

  if (isLoading) {
    return <p className="status-text">리포트를 불러오고 있습니다.</p>;
  }

  if (error) {
    return <ErrorState message="판매자 리포트를 불러오지 못했습니다." />;
  }

  if (!data) {
    return <EmptyState title="리포트 데이터가 없습니다" description="판매 활동이 생기면 이곳에 요약 지표가 표시됩니다." />;
  }

  const total = data.summary.total;
  const recent = data.summary.recent30Days;
  const hasAnyData =
    total.activeProductCount +
      total.soldOutProductCount +
      total.confirmedOrderCount +
      total.completedSettlementAmount +
      total.unsettledConfirmedAmount >
    0;

  return (
    <section className="list-page seller-report-page">
      <div className="list-page-header">
        <div>
          <h1>리포트</h1>
          <p>판매 상품, 주문, 정산 상태를 한눈에 확인합니다.</p>
        </div>
        <p className="status-text">생성 시각 {formatDateTime(data.generatedAt)}</p>
      </div>

      {!hasAnyData ? (
        <EmptyState title="아직 판매 데이터가 없습니다" description="상품을 등록하고 주문이 확정되면 요약 지표가 채워집니다." />
      ) : null}

      <section className="report-section" aria-labelledby="report-total-title">
        <div className="report-section-header">
          <h2 id="report-total-title">전체 누적</h2>
          <span className="muted-text">현재까지의 판매 활동</span>
        </div>
        <div className="metric-grid">
          <MetricCard label="판매중 상품" value={formatNumber(total.activeProductCount)} />
          <MetricCard label="판매완료 상품" value={formatNumber(total.soldOutProductCount)} />
          <MetricCard label="확정 주문" value={formatNumber(total.confirmedOrderCount)} />
          <MetricCard label="완료 정산액" value={`${formatCurrency(total.completedSettlementAmount)}원`} />
          <MetricCard label="미정산 확정 금액" value={`${formatCurrency(total.unsettledConfirmedAmount)}원`} />
        </div>
      </section>

      <section className="report-section" aria-labelledby="report-recent-title">
        <div className="report-section-header">
          <h2 id="report-recent-title">최근 {data.period.recentDays}일</h2>
          <span className="muted-text">
            {data.period.recentFrom} ~ {data.period.recentTo}
          </span>
        </div>
        <div className="metric-grid">
          <MetricCard label="신규 주문" value={formatNumber(recent.orderedCount)} />
          <MetricCard label="확정 주문" value={formatNumber(recent.confirmedOrderCount)} />
          <MetricCard label="완료 정산액" value={`${formatCurrency(recent.completedSettlementAmount)}원`} />
          <MetricCard label="미정산 확정 금액" value={`${formatCurrency(recent.unsettledConfirmedAmount)}원`} />
        </div>
      </section>

      <div className="report-distribution-grid">
        <StatusDistribution title="상품 상태" counts={data.productStatusCounts} />
        <StatusDistribution title="주문 상태" counts={data.orderStatusCounts} />
      </div>
    </section>
  );
}

function MetricCard({ label, value }: { label: string; value: string }) {
  return (
    <article className="metric-card">
      <span>{label}</span>
      <strong>{value}</strong>
    </article>
  );
}

function StatusDistribution({ title, counts }: { title: string; counts: SellerStatusCount[] }) {
  return (
    <section className="report-section" aria-label={title}>
      <div className="report-section-header">
        <h2>{title}</h2>
      </div>
      <div className="status-count-list">
        {counts.map((item) => (
          <div className="status-count-row" key={item.status}>
            <StatusBadge status={item.status} />
            <strong>{formatNumber(item.count)}</strong>
          </div>
        ))}
      </div>
    </section>
  );
}

function formatNumber(value: number) {
  return numberFormatter.format(value);
}

function formatCurrency(value: number) {
  return currencyFormatter.format(value);
}

function formatDateTime(value: string) {
  return dateFormatter.format(new Date(value));
}
```

- [ ] **Step 3: Add route**

Modify `web/src/app/router.tsx`.

Add import:

```ts
import { MyReportsPage } from '../pages/MyReportsPage';
```

Add this route after the `/me/settlements` route:

```tsx
<Route
  path="me/reports"
  element={
    <RequireAuth>
      <MyReportsPage />
    </RequireAuth>
  }
/>
```

- [ ] **Step 4: Add navigation link**

Modify `web/src/shared/layout/Shell.tsx`.

Add the reports link after the settlement link:

```tsx
<NavLink to="/me/reports">리포트</NavLink>
```

The logged-in nav block becomes:

```tsx
<>
  <NavLink to="/me/orders">내 주문</NavLink>
  <NavLink to="/me/sales">내 판매</NavLink>
  <NavLink to="/me/settlements">정산</NavLink>
  <NavLink to="/me/reports">리포트</NavLink>
  {member.role === 'ADMIN' ? <NavLink to="/admin/operations">관리자</NavLink> : null}
</>
```

- [ ] **Step 5: Add compact dashboard styles**

Modify `web/src/shared/styles.css`. Add these styles near existing page/list/admin styles:

```css
.seller-report-page {
  gap: 24px;
}

.report-section {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.report-section-header {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.report-section-header h2 {
  margin: 0;
  font-size: 1.1rem;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 12px;
}

.metric-card {
  display: flex;
  min-height: 96px;
  flex-direction: column;
  justify-content: space-between;
  gap: 12px;
  border: 1px solid #dfe6ee;
  border-radius: 8px;
  background: #ffffff;
  padding: 16px;
}

.metric-card span {
  color: #637282;
  font-size: 0.9rem;
}

.metric-card strong {
  font-size: 1.45rem;
  line-height: 1.2;
}

.report-distribution-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: 18px;
}

.status-count-list {
  display: flex;
  flex-direction: column;
  border: 1px solid #dfe6ee;
  border-radius: 8px;
  background: #ffffff;
}

.status-count-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-height: 48px;
  padding: 12px 14px;
}

.status-count-row + .status-count-row {
  border-top: 1px solid #dfe6ee;
}
```

- [ ] **Step 6: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 7: Commit web reports dashboard**

Run:

```powershell
git status --short
git add -- web/src/features/reports/sellerReportApi.ts web/src/pages/MyReportsPage.tsx web/src/app/router.tsx web/src/shared/layout/Shell.tsx web/src/shared/styles.css
git commit -m "feat: add seller reports dashboard page"
```

Expected: commit succeeds. `backend/src/main/resources/application.yaml` remains unstaged.

---

### Task 4: Final Verification

**Files:**
- Verify: all touched backend, web, and docs files.

- [ ] **Step 1: Run backend test suite**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: PASS.

- [ ] **Step 2: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 3: Run diff whitespace check**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 4: Confirm git hygiene**

Run:

```powershell
git status --short --branch --untracked-files=all
```

Expected shape after implementation commits:

```text
## main...origin/main [ahead 3]
 M backend/src/main/resources/application.yaml
```

The only unstaged change should be `backend/src/main/resources/application.yaml`.

- [ ] **Step 5: Write implementation handoff**

Create `docs/superpowers/handoffs/2026-06-17-milestone-12-seller-reports-dashboard-handoff.md`:

```markdown
# Milestone 12 Seller Reports Dashboard Handoff

## Completed

- Added `GET /api/seller/reports/dashboard`.
- Added seller-scoped all-time and recent 30-day summary metrics.
- Added product and order status distributions with zero-filled enum states.
- Added `/me/reports` web page and logged-in navigation link.

## Verification

- Backend tests: `.\gradlew.bat test`
- Web build: `npm run build`
- Diff check: `git diff --check`

## Local Notes

- `backend/src/main/resources/application.yaml` is a pre-existing local-only change and was not staged.

## Follow-Up Candidates

- Add custom date range filters.
- Add product-level ranking.
- Add simple trend charts after the aggregate API is stable.
```

- [ ] **Step 6: Commit handoff**

Run:

```powershell
git add -- docs/superpowers/handoffs/2026-06-17-milestone-12-seller-reports-dashboard-handoff.md
git commit -m "docs: add milestone 12 seller reports handoff"
```

Expected: commit succeeds.
