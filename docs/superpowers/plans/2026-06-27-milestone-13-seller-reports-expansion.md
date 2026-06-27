# Milestone 13 Seller Reports Expansion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expand seller reports with custom period metrics, product rankings, daily sales trends, recent confirmed sales, recent settlements, and a more practical `/me/reports` UI.

**Architecture:** Keep the existing Milestone 12 dashboard API unchanged and add a seller-scoped period report API under the existing `seller.report` package. Backend work uses DTO projection queries and service-level date expansion; frontend work extends the existing reports API module and replaces the current report page with filterable operational sections.

**Tech Stack:** Spring Boot, Spring MVC, Spring Security, Spring Data JPA, Hibernate, JUnit/MockMvc, React, TypeScript, TanStack Query, React Hook Form, Vite.

---

## File Structure

Backend files:

```text
backend/src/main/java/com/sweet/market/common/error/ErrorCode.java
backend/src/main/java/com/sweet/market/seller/report/SellerReportController.java
backend/src/main/java/com/sweet/market/seller/report/SellerReportQueryService.java
backend/src/main/java/com/sweet/market/seller/report/SellerPeriodReportResponse.java
backend/src/main/java/com/sweet/market/seller/report/SellerPeriodResponse.java
backend/src/main/java/com/sweet/market/seller/report/SellerPeriodSummaryResponse.java
backend/src/main/java/com/sweet/market/seller/report/SellerProductRankingResponse.java
backend/src/main/java/com/sweet/market/seller/report/SellerDailySalesResponse.java
backend/src/main/java/com/sweet/market/seller/report/SellerRecentSaleResponse.java
backend/src/main/java/com/sweet/market/seller/report/SellerRecentSettlementResponse.java
backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java
backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java
backend/src/test/java/com/sweet/market/seller/report/SellerReportApiTest.java
```

Frontend files:

```text
web/src/features/reports/sellerReportApi.ts
web/src/pages/MyReportsPage.tsx
web/src/shared/styles.css
```

Do not modify or stage:

```text
backend/src/main/resources/application.yaml
```

It has an existing local-only development change.

---

## Task 1: Backend Period Request Validation And Response Shell

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Modify: `backend/src/main/java/com/sweet/market/seller/report/SellerReportController.java`
- Modify: `backend/src/main/java/com/sweet/market/seller/report/SellerReportQueryService.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerPeriodReportResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerPeriodResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerPeriodSummaryResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerProductRankingResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerDailySalesResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerRecentSaleResponse.java`
- Create: `backend/src/main/java/com/sweet/market/seller/report/SellerRecentSettlementResponse.java`
- Test: `backend/src/test/java/com/sweet/market/seller/report/SellerReportApiTest.java`

- [ ] **Step 1: Add failing API tests for default period and invalid period requests**

Append these tests to `SellerReportApiTest` before helper methods:

```java
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
```

- [ ] **Step 2: Run the failing backend tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.seller.report.SellerReportApiTest
```

Expected: FAIL because `/api/seller/reports/period` is not implemented.

- [ ] **Step 3: Add report period error code**

In `ErrorCode.java`, add this enum value after `VALIDATION_ERROR`:

```java
    INVALID_REPORT_PERIOD(HttpStatus.BAD_REQUEST, "리포트 기간이 올바르지 않습니다."),
```

- [ ] **Step 4: Create response DTO records**

Create `SellerPeriodResponse.java`:

```java
package com.sweet.market.seller.report;

import java.time.LocalDate;

public record SellerPeriodResponse(
        LocalDate from,
        LocalDate to,
        long days
) {
}
```

Create `SellerPeriodSummaryResponse.java`:

```java
package com.sweet.market.seller.report;

public record SellerPeriodSummaryResponse(
        long orderedCount,
        long confirmedOrderCount,
        long confirmedSalesAmount,
        long completedSettlementAmount,
        long unsettledConfirmedAmount,
        long averageConfirmedOrderAmount
) {
}
```

Create `SellerProductRankingResponse.java`:

```java
package com.sweet.market.seller.report;

import java.time.LocalDateTime;

public record SellerProductRankingResponse(
        Long productId,
        String title,
        String thumbnailUrl,
        long confirmedOrderCount,
        long confirmedSalesAmount,
        LocalDateTime lastConfirmedAt
) {
}
```

Create `SellerDailySalesResponse.java`:

```java
package com.sweet.market.seller.report;

import java.time.LocalDate;

public record SellerDailySalesResponse(
        LocalDate date,
        long confirmedOrderCount,
        long confirmedSalesAmount
) {
}
```

Create `SellerRecentSaleResponse.java`:

```java
package com.sweet.market.seller.report;

import java.time.LocalDateTime;

public record SellerRecentSaleResponse(
        Long orderId,
        Long productId,
        String productTitle,
        String buyerNickname,
        long amount,
        LocalDateTime confirmedAt,
        String settlementStatus
) {
}
```

Create `SellerRecentSettlementResponse.java`:

```java
package com.sweet.market.seller.report;

import java.time.LocalDateTime;

public record SellerRecentSettlementResponse(
        Long settlementId,
        Long orderId,
        Long productId,
        String productTitle,
        long amount,
        String status,
        LocalDateTime settledAt
) {
}
```

Create `SellerPeriodReportResponse.java`:

```java
package com.sweet.market.seller.report;

import java.time.LocalDateTime;
import java.util.List;

public record SellerPeriodReportResponse(
        LocalDateTime generatedAt,
        SellerPeriodResponse period,
        SellerPeriodSummaryResponse summary,
        List<SellerProductRankingResponse> productRankings,
        List<SellerDailySalesResponse> dailySales,
        List<SellerRecentSaleResponse> recentSales,
        List<SellerRecentSettlementResponse> recentSettlements
) {
}
```

- [ ] **Step 5: Add the period endpoint to the controller**

Modify `SellerReportController.java` imports:

```java
import org.springframework.web.bind.annotation.RequestParam;
```

Add this method below `dashboard(...)`:

```java
    @GetMapping("/period")
    public ApiResponse<SellerPeriodReportResponse> period(
            Authentication authentication,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(sellerReportQueryService.getPeriodReport(member.id(), from, to));
    }
```

- [ ] **Step 6: Add the service shell with period validation**

Modify `SellerReportQueryService.java` imports:

```java
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
```

Add constants near `RECENT_DAYS`:

```java
    private static final int MAX_PERIOD_DAYS = 180;
```

Add this method below `getDashboard(...)`:

```java
    public SellerPeriodReportResponse getPeriodReport(Long sellerId, String fromValue, String toValue) {
        SellerPeriodRange period = resolvePeriod(fromValue, toValue);

        SellerPeriodSummaryResponse summary = new SellerPeriodSummaryResponse(0, 0, 0, 0, 0, 0);

        return new SellerPeriodReportResponse(
                LocalDateTime.now(),
                new SellerPeriodResponse(period.from(), period.to(), period.days()),
                summary,
                Collections.emptyList(),
                zeroFilledDailySales(period.from(), period.to(), Collections.emptyList()),
                Collections.emptyList(),
                Collections.emptyList()
        );
    }
```

Add these private helpers near the bottom of the service, above `zeroIfNull(...)`:

```java
    private SellerPeriodRange resolvePeriod(String fromValue, String toValue) {
        if (fromValue == null && toValue == null) {
            LocalDate to = LocalDate.now();
            LocalDate from = to.minusDays(RECENT_DAYS - 1L);
            return validatePeriod(from, to);
        }

        if (fromValue == null || toValue == null) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }

        try {
            return validatePeriod(LocalDate.parse(fromValue), LocalDate.parse(toValue));
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }
    }

    private SellerPeriodRange validatePeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }

        long days = ChronoUnit.DAYS.between(from, to) + 1L;
        if (days > MAX_PERIOD_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_REPORT_PERIOD);
        }

        return new SellerPeriodRange(from, to, days);
    }

    private List<SellerDailySalesResponse> zeroFilledDailySales(
            LocalDate from,
            LocalDate to,
            List<SellerDailySalesResponse> rows
    ) {
        Map<LocalDate, SellerDailySalesResponse> rowByDate = rows.stream()
                .collect(Collectors.toMap(SellerDailySalesResponse::date, Function.identity()));

        List<SellerDailySalesResponse> result = new ArrayList<>();
        LocalDate current = from;
        while (!current.isAfter(to)) {
            SellerDailySalesResponse row = rowByDate.getOrDefault(current, new SellerDailySalesResponse(current, 0, 0));
            result.add(row);
            current = current.plusDays(1);
        }
        return result;
    }

    private record SellerPeriodRange(LocalDate from, LocalDate to, long days) {

        LocalDateTime fromInclusive() {
            return from.atStartOfDay();
        }

        LocalDateTime toExclusive() {
            return to.plusDays(1).atStartOfDay();
        }
    }
```

- [ ] **Step 7: Run tests for Task 1**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.seller.report.SellerReportApiTest
```

Expected: PASS for the new shell and existing dashboard tests.

- [ ] **Step 8: Commit Task 1**

Run:

```powershell
git add -- backend/src/main/java/com/sweet/market/common/error/ErrorCode.java `
  backend/src/main/java/com/sweet/market/seller/report/SellerReportController.java `
  backend/src/main/java/com/sweet/market/seller/report/SellerReportQueryService.java `
  backend/src/main/java/com/sweet/market/seller/report/SellerPeriodReportResponse.java `
  backend/src/main/java/com/sweet/market/seller/report/SellerPeriodResponse.java `
  backend/src/main/java/com/sweet/market/seller/report/SellerPeriodSummaryResponse.java `
  backend/src/main/java/com/sweet/market/seller/report/SellerProductRankingResponse.java `
  backend/src/main/java/com/sweet/market/seller/report/SellerDailySalesResponse.java `
  backend/src/main/java/com/sweet/market/seller/report/SellerRecentSaleResponse.java `
  backend/src/main/java/com/sweet/market/seller/report/SellerRecentSettlementResponse.java `
  backend/src/test/java/com/sweet/market/seller/report/SellerReportApiTest.java
git commit -m "feat: add seller period report shell"
```

Do not stage `backend/src/main/resources/application.yaml`.

---

## Task 2: Backend Period Summary And Daily Sales

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/seller/report/SellerReportQueryService.java`
- Test: `backend/src/test/java/com/sweet/market/seller/report/SellerReportApiTest.java`

- [ ] **Step 1: Add failing tests for custom period metrics and daily zero fill**

Append these tests to `SellerReportApiTest` before helper methods:

```java
    @Test
    void 기간_리포트는_선택한_기간의_요약을_집계한다() throws Exception {
        String token = createMemberAndLogin("seller-period-summary@example.com", "seller-period-summary");
        Member seller = memberRepository.findAll().get(0);
        Member buyer = saveMember("buyer-period-summary@example.com", "buyer-period-summary");

        LocalDate from = LocalDate.now().minusDays(6);
        LocalDate to = LocalDate.now();
        LocalDateTime inside = from.plusDays(1).atTime(10, 0);
        LocalDateTime outside = from.minusDays(1).atTime(10, 0);

        Product orderedOnly = saveProduct(seller, "Ordered only", 10_000);
        Product confirmedOne = saveProduct(seller, "Confirmed one", 20_000);
        Product confirmedTwo = saveProduct(seller, "Confirmed two", 40_000);
        Product outsideProduct = saveProduct(seller, "Outside", 80_000);

        saveConfirmedOrder(buyer, orderedOnly, inside, outside);
        saveConfirmedOrder(buyer, confirmedOne, outside, inside);
        Order settledOrder = saveConfirmedOrder(buyer, confirmedTwo, outside, inside.plusDays(1));
        saveSettlement(settledOrder, inside.plusDays(2));
        Order outsideOrder = saveConfirmedOrder(buyer, outsideProduct, outside, outside);
        saveSettlement(outsideOrder, outside);

        mockMvc.perform(get("/api/seller/reports/period")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.orderedCount").value(1))
                .andExpect(jsonPath("$.data.summary.confirmedOrderCount").value(2))
                .andExpect(jsonPath("$.data.summary.confirmedSalesAmount").value(60_000))
                .andExpect(jsonPath("$.data.summary.completedSettlementAmount").value(40_000))
                .andExpect(jsonPath("$.data.summary.unsettledConfirmedAmount").value(20_000))
                .andExpect(jsonPath("$.data.summary.averageConfirmedOrderAmount").value(30_000));
    }

    @Test
    void 일별_판매_추세는_주문이_없는_날도_0으로_포함한다() throws Exception {
        String token = createMemberAndLogin("seller-daily-sales@example.com", "seller-daily-sales");
        Member seller = memberRepository.findAll().get(0);
        Member buyer = saveMember("buyer-daily-sales@example.com", "buyer-daily-sales");

        LocalDate from = LocalDate.now().minusDays(2);
        LocalDate to = LocalDate.now();

        saveConfirmedOrder(buyer, saveProduct(seller, "Day one", 10_000), from.atTime(8, 0));
        saveConfirmedOrder(buyer, saveProduct(seller, "Day three", 30_000), to.atTime(9, 0));

        mockMvc.perform(get("/api/seller/reports/period")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailySales.length()").value(3))
                .andExpect(jsonPath("$.data.dailySales[0].date").value(from.toString()))
                .andExpect(jsonPath("$.data.dailySales[0].confirmedOrderCount").value(1))
                .andExpect(jsonPath("$.data.dailySales[0].confirmedSalesAmount").value(10_000))
                .andExpect(jsonPath("$.data.dailySales[1].date").value(from.plusDays(1).toString()))
                .andExpect(jsonPath("$.data.dailySales[1].confirmedOrderCount").value(0))
                .andExpect(jsonPath("$.data.dailySales[1].confirmedSalesAmount").value(0))
                .andExpect(jsonPath("$.data.dailySales[2].date").value(to.toString()))
                .andExpect(jsonPath("$.data.dailySales[2].confirmedOrderCount").value(1))
                .andExpect(jsonPath("$.data.dailySales[2].confirmedSalesAmount").value(30_000));
    }

    @Test
    void 기간_리포트_요약은_다른_판매자의_데이터를_포함하지_않는다() throws Exception {
        String token = createMemberAndLogin("seller-period-scope@example.com", "seller-period-scope");
        Member targetSeller = memberRepository.findAll().get(0);
        Member otherSeller = saveMember("other-period-scope@example.com", "other-period-scope");
        Member buyer = saveMember("buyer-period-scope@example.com", "buyer-period-scope");

        LocalDate from = LocalDate.now().minusDays(6);
        LocalDate to = LocalDate.now();
        LocalDateTime inside = LocalDateTime.now().minusDays(1);

        saveConfirmedOrder(buyer, saveProduct(targetSeller, "Target", 10_000), inside);
        Order otherOrder = saveConfirmedOrder(buyer, saveProduct(otherSeller, "Other", 100_000), inside);
        saveSettlement(otherOrder, inside);

        mockMvc.perform(get("/api/seller/reports/period")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.confirmedOrderCount").value(1))
                .andExpect(jsonPath("$.data.summary.confirmedSalesAmount").value(10_000))
                .andExpect(jsonPath("$.data.summary.completedSettlementAmount").value(0))
                .andExpect(jsonPath("$.data.summary.unsettledConfirmedAmount").value(10_000));
    }
```

- [ ] **Step 2: Run failing tests for period metrics**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.seller.report.SellerReportApiTest
```

Expected: FAIL because summary and daily queries still return zero shell values.

- [ ] **Step 3: Add order summary and daily sales repository queries**

Add these methods to `OrderRepository.java` below existing seller report aggregate methods:

```java
    @Query("""
            select coalesce(sum(p.price), 0)
            from Order o
            join o.product p
            where p.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
              and o.confirmedAt >= :fromInclusive
              and o.confirmedAt < :toExclusive
            """)
    Long sumConfirmedSalesAmountBySellerIdAndConfirmedAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );

    @Query("""
            select new com.sweet.market.seller.report.SellerDailySalesResponse(
                cast(o.confirmedAt as localdate),
                count(o),
                coalesce(sum(p.price), 0)
            )
            from Order o
            join o.product p
            where p.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
              and o.confirmedAt >= :fromInclusive
              and o.confirmedAt < :toExclusive
            group by cast(o.confirmedAt as localdate)
            order by cast(o.confirmedAt as localdate) asc
            """)
    List<com.sweet.market.seller.report.SellerDailySalesResponse> findDailyConfirmedSalesBySellerIdAndConfirmedAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive
    );
```

- [ ] **Step 4: Use the new queries in `SellerReportQueryService`**

Replace the shell summary block in `getPeriodReport(...)` with:

```java
        LocalDateTime fromInclusive = period.fromInclusive();
        LocalDateTime toExclusive = period.toExclusive();

        long orderedCount = orderRepository.countOrdersBySellerIdAndOrderedAtBetween(
                sellerId,
                fromInclusive,
                toExclusive
        );
        long confirmedOrderCount = orderRepository.countConfirmedOrdersBySellerIdAndConfirmedAtBetween(
                sellerId,
                fromInclusive,
                toExclusive
        );
        long confirmedSalesAmount = zeroIfNull(orderRepository.sumConfirmedSalesAmountBySellerIdAndConfirmedAtBetween(
                sellerId,
                fromInclusive,
                toExclusive
        ));
        long completedSettlementAmount = zeroIfNull(settlementRepository.sumCompletedAmountBySellerIdAndSettledAtBetween(
                sellerId,
                fromInclusive,
                toExclusive
        ));
        long unsettledConfirmedAmount = zeroIfNull(orderRepository.sumUnsettledConfirmedAmountBySellerIdAndConfirmedAtBetween(
                sellerId,
                fromInclusive,
                toExclusive
        ));
        long averageConfirmedOrderAmount = confirmedOrderCount == 0 ? 0 : confirmedSalesAmount / confirmedOrderCount;

        SellerPeriodSummaryResponse summary = new SellerPeriodSummaryResponse(
                orderedCount,
                confirmedOrderCount,
                confirmedSalesAmount,
                completedSettlementAmount,
                unsettledConfirmedAmount,
                averageConfirmedOrderAmount
        );
        List<SellerDailySalesResponse> dailySales = zeroFilledDailySales(
                period.from(),
                period.to(),
                orderRepository.findDailyConfirmedSalesBySellerIdAndConfirmedAtBetween(
                        sellerId,
                        fromInclusive,
                        toExclusive
                )
        );
```

Then update the response construction to use `dailySales`:

```java
                summary,
                Collections.emptyList(),
                dailySales,
                Collections.emptyList(),
                Collections.emptyList()
```

- [ ] **Step 5: Run Task 2 tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.seller.report.SellerReportApiTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

Run:

```powershell
git add -- backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java `
  backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java `
  backend/src/main/java/com/sweet/market/seller/report/SellerReportQueryService.java `
  backend/src/test/java/com/sweet/market/seller/report/SellerReportApiTest.java
git commit -m "feat: add seller period summary metrics"
```

---

## Task 3: Backend Product Ranking, Recent Sales, And Recent Settlements

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/seller/report/SellerReportQueryService.java`
- Test: `backend/src/test/java/com/sweet/market/seller/report/SellerReportApiTest.java`

- [ ] **Step 1: Add failing tests for ranking and recent rows**

Append these tests to `SellerReportApiTest` before helper methods:

```java
    @Test
    void 상품_랭킹은_확정_판매액과_건수와_최근_확정일과_ID순으로_정렬된다() throws Exception {
        String token = createMemberAndLogin("seller-ranking@example.com", "seller-ranking");
        Member seller = memberRepository.findAll().get(0);
        Member buyer = saveMember("buyer-ranking@example.com", "buyer-ranking");

        LocalDate from = LocalDate.now().minusDays(6);
        LocalDate to = LocalDate.now();

        Product lowAmount = saveProduct(seller, "Low Amount", 10_000);
        Product highCount = saveProduct(seller, "High Count", 20_000);
        Product highRecent = saveProduct(seller, "High Recent", 40_000);

        saveConfirmedOrder(buyer, lowAmount, from.atTime(9, 0));
        saveConfirmedOrder(buyer, highCount, from.atTime(10, 0));
        saveConfirmedOrder(buyer, highCount, from.plusDays(1).atTime(10, 0));
        saveConfirmedOrder(buyer, highRecent, from.plusDays(2).atTime(10, 0));

        mockMvc.perform(get("/api/seller/reports/period")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productRankings[0].title").value("High Count"))
                .andExpect(jsonPath("$.data.productRankings[0].confirmedOrderCount").value(2))
                .andExpect(jsonPath("$.data.productRankings[0].confirmedSalesAmount").value(40_000))
                .andExpect(jsonPath("$.data.productRankings[1].title").value("High Recent"))
                .andExpect(jsonPath("$.data.productRankings[1].confirmedOrderCount").value(1))
                .andExpect(jsonPath("$.data.productRankings[1].confirmedSalesAmount").value(40_000))
                .andExpect(jsonPath("$.data.productRankings[2].title").value("Low Amount"));
    }

    @Test
    void 최근_판매와_정산은_최신순으로_최대_10개를_반환한다() throws Exception {
        String token = createMemberAndLogin("seller-recent-rows@example.com", "seller-recent-rows");
        Member seller = memberRepository.findAll().get(0);
        Member buyer = saveMember("buyer-recent-rows@example.com", "buyer-recent-rows");

        LocalDate from = LocalDate.now().minusDays(20);
        LocalDate to = LocalDate.now();

        for (int index = 1; index <= 12; index++) {
            Product product = saveProduct(seller, "Recent Product " + index, index * 1_000L);
            Order order = saveConfirmedOrder(buyer, product, from.plusDays(index).atTime(10, 0));
            saveSettlement(order, from.plusDays(index).atTime(11, 0));
        }

        mockMvc.perform(get("/api/seller/reports/period")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recentSales.length()").value(10))
                .andExpect(jsonPath("$.data.recentSales[0].productTitle").value("Recent Product 12"))
                .andExpect(jsonPath("$.data.recentSales[0].settlementStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.recentSales[9].productTitle").value("Recent Product 3"))
                .andExpect(jsonPath("$.data.recentSettlements.length()").value(10))
                .andExpect(jsonPath("$.data.recentSettlements[0].productTitle").value("Recent Product 12"))
                .andExpect(jsonPath("$.data.recentSettlements[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.recentSettlements[9].productTitle").value("Recent Product 3"));
    }

    @Test
    void 랭킹과_최근_목록은_다른_판매자_데이터를_포함하지_않는다() throws Exception {
        String token = createMemberAndLogin("seller-ranking-scope@example.com", "seller-ranking-scope");
        Member targetSeller = memberRepository.findAll().get(0);
        Member otherSeller = saveMember("other-ranking-scope@example.com", "other-ranking-scope");
        Member buyer = saveMember("buyer-ranking-scope@example.com", "buyer-ranking-scope");

        LocalDate from = LocalDate.now().minusDays(6);
        LocalDate to = LocalDate.now();
        LocalDateTime inside = LocalDateTime.now().minusDays(1);

        saveConfirmedOrder(buyer, saveProduct(targetSeller, "Target Scope", 10_000), inside);
        Order otherOrder = saveConfirmedOrder(buyer, saveProduct(otherSeller, "Other Scope", 100_000), inside);
        saveSettlement(otherOrder, inside);

        mockMvc.perform(get("/api/seller/reports/period")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productRankings.length()").value(1))
                .andExpect(jsonPath("$.data.productRankings[0].title").value("Target Scope"))
                .andExpect(jsonPath("$.data.recentSales.length()").value(1))
                .andExpect(jsonPath("$.data.recentSales[0].productTitle").value("Target Scope"))
                .andExpect(jsonPath("$.data.recentSettlements.length()").value(0));
    }
```

- [ ] **Step 2: Run failing tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.seller.report.SellerReportApiTest
```

Expected: FAIL because ranking and recent row lists are empty.

- [ ] **Step 3: Add order repository projection queries**

Add `import org.springframework.data.domain.Pageable;` if not already present. It is already present in the current file.

Add these methods to `OrderRepository.java`:

```java
    @Query("""
            select new com.sweet.market.seller.report.SellerProductRankingResponse(
                p.id,
                p.title,
                (
                    select min(i.imageUrl)
                    from ProductImage i
                    where i.product = p
                ),
                count(o),
                coalesce(sum(p.price), 0),
                max(o.confirmedAt)
            )
            from Order o
            join o.product p
            where p.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
              and o.confirmedAt >= :fromInclusive
              and o.confirmedAt < :toExclusive
            group by p.id, p.title
            order by coalesce(sum(p.price), 0) desc, count(o) desc, max(o.confirmedAt) desc, p.id desc
            """)
    List<com.sweet.market.seller.report.SellerProductRankingResponse> findTopProductRankingsBySellerIdAndConfirmedAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable
    );

    @Query("""
            select new com.sweet.market.seller.report.SellerRecentSaleResponse(
                o.id,
                p.id,
                p.title,
                buyer.nickname,
                p.price,
                o.confirmedAt,
                coalesce(cast(s.status as string), 'NONE')
            )
            from Order o
            join o.buyer buyer
            join o.product p
            left join Settlement s on s.order = o
            where p.seller.id = :sellerId
              and o.status = com.sweet.market.order.domain.OrderStatus.CONFIRMED
              and o.confirmedAt >= :fromInclusive
              and o.confirmedAt < :toExclusive
            order by o.confirmedAt desc, o.id desc
            """)
    List<com.sweet.market.seller.report.SellerRecentSaleResponse> findRecentConfirmedSalesBySellerIdAndConfirmedAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable
    );
```

- [ ] **Step 4: Add settlement repository recent rows query**

Add this method to `SettlementRepository.java`:

```java
    @Query("""
            select new com.sweet.market.seller.report.SellerRecentSettlementResponse(
                s.id,
                o.id,
                p.id,
                p.title,
                s.amount,
                cast(s.status as string),
                s.settledAt
            )
            from Settlement s
            join s.order o
            join o.product p
            where s.seller.id = :sellerId
              and s.settledAt >= :fromInclusive
              and s.settledAt < :toExclusive
            order by s.settledAt desc, s.id desc
            """)
    List<com.sweet.market.seller.report.SellerRecentSettlementResponse> findRecentSettlementsBySellerIdAndSettledAtBetween(
            @Param("sellerId") Long sellerId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            Pageable pageable
    );
```

- [ ] **Step 5: Query ranking and recent rows in the service**

Add import to `SellerReportQueryService.java`:

```java
import org.springframework.data.domain.PageRequest;
```

Add constants near the other constants:

```java
    private static final int PRODUCT_RANKING_LIMIT = 5;
    private static final int RECENT_ROW_LIMIT = 10;
```

Inside `getPeriodReport(...)`, after `dailySales`, add:

```java
        List<SellerProductRankingResponse> productRankings =
                orderRepository.findTopProductRankingsBySellerIdAndConfirmedAtBetween(
                        sellerId,
                        fromInclusive,
                        toExclusive,
                        PageRequest.of(0, PRODUCT_RANKING_LIMIT)
                );
        List<SellerRecentSaleResponse> recentSales =
                orderRepository.findRecentConfirmedSalesBySellerIdAndConfirmedAtBetween(
                        sellerId,
                        fromInclusive,
                        toExclusive,
                        PageRequest.of(0, RECENT_ROW_LIMIT)
                );
        List<SellerRecentSettlementResponse> recentSettlements =
                settlementRepository.findRecentSettlementsBySellerIdAndSettledAtBetween(
                        sellerId,
                        fromInclusive,
                        toExclusive,
                        PageRequest.of(0, RECENT_ROW_LIMIT)
                );
```

Replace the response list arguments with:

```java
                productRankings,
                dailySales,
                recentSales,
                recentSettlements
```

- [ ] **Step 6: Run Task 3 tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.seller.report.SellerReportApiTest
```

Expected: PASS.

- [ ] **Step 7: Commit Task 3**

Run:

```powershell
git add -- backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java `
  backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java `
  backend/src/main/java/com/sweet/market/seller/report/SellerReportQueryService.java `
  backend/src/test/java/com/sweet/market/seller/report/SellerReportApiTest.java
git commit -m "feat: add seller report ranking and recent rows"
```

---

## Task 4: Frontend Report API Types

**Files:**

- Modify: `web/src/features/reports/sellerReportApi.ts`

- [ ] **Step 1: Extend TypeScript report types**

Replace `web/src/features/reports/sellerReportApi.ts` with:

```ts
import { api } from '../../shared/api/http';

export type ProductStatus = 'ON_SALE' | 'RESERVED' | 'SOLD_OUT' | 'HIDDEN';
export type OrderStatus = 'CREATED' | 'PAID' | 'SHIPPING' | 'DELIVERED' | 'CONFIRMED' | 'CANCELED';
export type SettlementStatus = 'COMPLETED' | 'FAILED' | 'NONE';

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

export type SellerProductStatusCount = {
  status: ProductStatus;
  count: number;
};

export type SellerOrderStatusCount = {
  status: OrderStatus;
  count: number;
};

export type SellerDashboardReport = {
  generatedAt: string;
  period: SellerReportPeriod;
  summary: SellerReportSummary;
  productStatusCounts: SellerProductStatusCount[];
  orderStatusCounts: SellerOrderStatusCount[];
};

export type SellerPeriodInput = {
  from: string;
  to: string;
};

export type SellerPeriod = {
  from: string;
  to: string;
  days: number;
};

export type SellerPeriodSummary = {
  orderedCount: number;
  confirmedOrderCount: number;
  confirmedSalesAmount: number;
  completedSettlementAmount: number;
  unsettledConfirmedAmount: number;
  averageConfirmedOrderAmount: number;
};

export type SellerProductRanking = {
  productId: number;
  title: string;
  thumbnailUrl: string | null;
  confirmedOrderCount: number;
  confirmedSalesAmount: number;
  lastConfirmedAt: string;
};

export type SellerDailySales = {
  date: string;
  confirmedOrderCount: number;
  confirmedSalesAmount: number;
};

export type SellerRecentSale = {
  orderId: number;
  productId: number;
  productTitle: string;
  buyerNickname: string;
  amount: number;
  confirmedAt: string;
  settlementStatus: SettlementStatus;
};

export type SellerRecentSettlement = {
  settlementId: number;
  orderId: number;
  productId: number;
  productTitle: string;
  amount: number;
  status: Exclude<SettlementStatus, 'NONE'>;
  settledAt: string;
};

export type SellerPeriodReport = {
  generatedAt: string;
  period: SellerPeriod;
  summary: SellerPeriodSummary;
  productRankings: SellerProductRanking[];
  dailySales: SellerDailySales[];
  recentSales: SellerRecentSale[];
  recentSettlements: SellerRecentSettlement[];
};

export function getSellerDashboardReport() {
  return api<SellerDashboardReport>('/api/seller/reports/dashboard');
}

export function getSellerPeriodReport(input: SellerPeriodInput) {
  const params = new URLSearchParams({
    from: input.from,
    to: input.to,
  });

  return api<SellerPeriodReport>(`/api/seller/reports/period?${params.toString()}`);
}
```

- [ ] **Step 2: Run the web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS because no page uses the new API yet.

- [ ] **Step 3: Commit Task 4**

Run:

```powershell
git add -- web/src/features/reports/sellerReportApi.ts
git commit -m "feat: add seller period report web api"
```

---

## Task 5: Frontend Reports Page Expansion

**Files:**

- Modify: `web/src/pages/MyReportsPage.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Replace `MyReportsPage.tsx` with the expanded page**

Replace the file with this implementation:

```tsx
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { useAuth } from '../features/auth/AuthProvider';
import {
  getSellerDashboardReport,
  getSellerPeriodReport,
  type SellerDailySales,
  type SellerPeriodInput,
  type SellerProductRanking,
  type SellerRecentSale,
  type SellerRecentSettlement,
} from '../features/reports/sellerReportApi';
import { EmptyState, ErrorState, StatusBadge } from '../shared/ui/ResourceStates';
import { useMemo, useState } from 'react';

const currencyFormatter = new Intl.NumberFormat('ko-KR');
const numberFormatter = new Intl.NumberFormat('ko-KR');
const dateOnlyFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
});
const dateFormatter = new Intl.DateTimeFormat('ko-KR', {
  dateStyle: 'medium',
  timeStyle: 'short',
});

type StatusCount = {
  status: string;
  count: number;
};

export function MyReportsPage() {
  const { member } = useAuth();
  const memberId = member?.id;
  const defaultPeriod = useMemo(() => getDefaultPeriod(30), []);
  const [periodInput, setPeriodInput] = useState<SellerPeriodInput>(defaultPeriod);
  const [draftPeriod, setDraftPeriod] = useState<SellerPeriodInput>(defaultPeriod);
  const validationMessage = validatePeriod(draftPeriod);

  const dashboardQuery = useQuery({
    queryKey: ['seller-dashboard-report', memberId],
    queryFn: getSellerDashboardReport,
    enabled: memberId !== undefined,
  });
  const periodQuery = useQuery({
    queryKey: ['seller-period-report', memberId, periodInput.from, periodInput.to],
    queryFn: () => getSellerPeriodReport(periodInput),
    enabled: memberId !== undefined,
  });

  if (dashboardQuery.isLoading || periodQuery.isLoading) {
    return <p className="status-text">리포트를 불러오고 있습니다.</p>;
  }

  if (dashboardQuery.error || periodQuery.error) {
    return <ErrorState message="판매자 리포트를 불러오지 못했습니다." />;
  }

  if (!dashboardQuery.data || !periodQuery.data) {
    return <EmptyState title="리포트 데이터가 없습니다" description="판매 활동이 생기면 이곳에 지표가 표시됩니다." />;
  }

  const dashboard = dashboardQuery.data;
  const period = periodQuery.data;
  const total = dashboard.summary.total;
  const recent = dashboard.summary.recent30Days;
  const hasAnyData =
    total.activeProductCount +
      total.soldOutProductCount +
      total.confirmedOrderCount +
      total.completedSettlementAmount +
      total.unsettledConfirmedAmount +
      recent.orderedCount +
      recent.confirmedOrderCount +
      recent.completedSettlementAmount +
      recent.unsettledConfirmedAmount +
      period.summary.orderedCount +
      period.summary.confirmedOrderCount +
      period.summary.confirmedSalesAmount +
      period.summary.completedSettlementAmount +
      period.summary.unsettledConfirmedAmount +
      sumCounts(dashboard.productStatusCounts) +
      sumCounts(dashboard.orderStatusCounts) >
    0;

  return (
    <section className="list-page seller-report-page">
      <div className="list-page-header report-page-header">
        <div>
          <h1>리포트</h1>
          <p>판매 상품, 주문, 정산 흐름을 기간별로 확인합니다.</p>
        </div>
        <p className="status-text">생성 시각 {formatDateTime(period.generatedAt)}</p>
      </div>

      <ReportFilter
        draftPeriod={draftPeriod}
        isFetching={periodQuery.isFetching}
        validationMessage={validationMessage}
        onChange={setDraftPeriod}
        onQuickRange={(days) => {
          const nextPeriod = getDefaultPeriod(days);
          setDraftPeriod(nextPeriod);
          setPeriodInput(nextPeriod);
        }}
        onSubmit={() => {
          if (!validationMessage) {
            setPeriodInput(draftPeriod);
          }
        }}
      />

      {!hasAnyData ? (
        <EmptyState title="아직 판매 데이터가 없습니다" description="상품을 등록하고 주문이 확정되면 리포트가 채워집니다." />
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

      <section className="report-section" aria-labelledby="report-period-title">
        <div className="report-section-header">
          <h2 id="report-period-title">선택 기간</h2>
          <span className="muted-text">
            {formatDate(period.period.from)} ~ {formatDate(period.period.to)} · {formatNumber(period.period.days)}일
          </span>
        </div>
        <div className="metric-grid">
          <MetricCard label="신규 주문" value={formatNumber(period.summary.orderedCount)} />
          <MetricCard label="확정 주문" value={formatNumber(period.summary.confirmedOrderCount)} />
          <MetricCard label="확정 판매액" value={`${formatCurrency(period.summary.confirmedSalesAmount)}원`} />
          <MetricCard label="평균 주문액" value={`${formatCurrency(period.summary.averageConfirmedOrderAmount)}원`} />
          <MetricCard label="완료 정산액" value={`${formatCurrency(period.summary.completedSettlementAmount)}원`} />
          <MetricCard label="미정산 확정 금액" value={`${formatCurrency(period.summary.unsettledConfirmedAmount)}원`} />
        </div>
      </section>

      <DailySalesTrend rows={period.dailySales} />

      <ProductRanking rankings={period.productRankings} />

      <div className="report-table-grid">
        <RecentSales rows={period.recentSales} />
        <RecentSettlements rows={period.recentSettlements} />
      </div>

      <div className="report-distribution-grid">
        <StatusDistribution title={`최근 ${dashboard.period.recentDays}일 요약`} counts={[
          { status: '신규 주문', count: recent.orderedCount },
          { status: '확정 주문', count: recent.confirmedOrderCount },
        ]} />
        <StatusDistribution title="상품 상태" counts={dashboard.productStatusCounts} />
        <StatusDistribution title="주문 상태" counts={dashboard.orderStatusCounts} />
      </div>
    </section>
  );
}

function ReportFilter({
  draftPeriod,
  isFetching,
  validationMessage,
  onChange,
  onQuickRange,
  onSubmit,
}: {
  draftPeriod: SellerPeriodInput;
  isFetching: boolean;
  validationMessage: string | null;
  onChange: (period: SellerPeriodInput) => void;
  onQuickRange: (days: number) => void;
  onSubmit: () => void;
}) {
  return (
    <section className="report-filter" aria-label="리포트 기간 필터">
      <div className="report-filter-fields">
        <label>
          시작일
          <input
            type="date"
            value={draftPeriod.from}
            onChange={(event) => onChange({ ...draftPeriod, from: event.target.value })}
          />
        </label>
        <label>
          종료일
          <input
            type="date"
            value={draftPeriod.to}
            onChange={(event) => onChange({ ...draftPeriod, to: event.target.value })}
          />
        </label>
        <button className="text-button" type="button" disabled={Boolean(validationMessage) || isFetching} onClick={onSubmit}>
          {isFetching ? '조회 중' : '조회'}
        </button>
      </div>
      <div className="report-quick-ranges">
        {[7, 30, 90].map((days) => (
          <button key={days} type="button" className="report-chip-button" onClick={() => onQuickRange(days)}>
            {days}일
          </button>
        ))}
      </div>
      {validationMessage ? <p className="error-text">{validationMessage}</p> : null}
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

function DailySalesTrend({ rows }: { rows: SellerDailySales[] }) {
  const maxAmount = Math.max(...rows.map((row) => row.confirmedSalesAmount), 0);

  return (
    <section className="report-section" aria-labelledby="daily-sales-title">
      <div className="report-section-header">
        <h2 id="daily-sales-title">일별 확정 판매</h2>
      </div>
      <div className="daily-sales-list">
        {rows.map((row) => {
          const width = maxAmount === 0 ? 0 : Math.max(6, Math.round((row.confirmedSalesAmount / maxAmount) * 100));
          return (
            <div className="daily-sales-row" key={row.date}>
              <span>{formatDate(row.date)}</span>
              <div className="daily-sales-bar-track">
                <div className="daily-sales-bar" style={{ width: `${width}%` }} />
              </div>
              <strong>{formatCurrency(row.confirmedSalesAmount)}원</strong>
              <span>{formatNumber(row.confirmedOrderCount)}건</span>
            </div>
          );
        })}
      </div>
    </section>
  );
}

function ProductRanking({ rankings }: { rankings: SellerProductRanking[] }) {
  return (
    <section className="report-section" aria-labelledby="product-ranking-title">
      <div className="report-section-header">
        <h2 id="product-ranking-title">상품별 판매 랭킹</h2>
      </div>
      {rankings.length === 0 ? (
        <EmptyState title="랭킹 데이터가 없습니다" description="선택 기간에 확정된 판매가 없습니다." />
      ) : (
        <div className="product-ranking-list">
          {rankings.map((item, index) => (
            <Link className="product-ranking-row" key={item.productId} to={`/products/${item.productId}`}>
              <span className="ranking-number">{index + 1}</span>
              {item.thumbnailUrl ? (
                <img className="ranking-thumb" src={item.thumbnailUrl} alt="" />
              ) : (
                <span className="ranking-thumb ranking-thumb-fallback">Sweet Market</span>
              )}
              <span className="ranking-title">{item.title}</span>
              <strong>{formatCurrency(item.confirmedSalesAmount)}원</strong>
              <span>{formatNumber(item.confirmedOrderCount)}건</span>
              <span>{formatDateTime(item.lastConfirmedAt)}</span>
            </Link>
          ))}
        </div>
      )}
    </section>
  );
}

function RecentSales({ rows }: { rows: SellerRecentSale[] }) {
  return (
    <section className="report-section" aria-label="최근 확정 판매">
      <div className="report-section-header">
        <h2>최근 확정 판매</h2>
      </div>
      {rows.length === 0 ? (
        <EmptyState title="최근 판매가 없습니다" description="선택 기간에 확정된 판매가 없습니다." />
      ) : (
        <div className="report-record-list">
          {rows.map((row) => (
            <div className="report-record-row" key={row.orderId}>
              <Link to={`/products/${row.productId}`}>{row.productTitle}</Link>
              <span>{row.buyerNickname}</span>
              <strong>{formatCurrency(row.amount)}원</strong>
              <StatusBadge status={row.settlementStatus} />
              <span>{formatDateTime(row.confirmedAt)}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function RecentSettlements({ rows }: { rows: SellerRecentSettlement[] }) {
  return (
    <section className="report-section" aria-label="최근 정산">
      <div className="report-section-header">
        <h2>최근 정산</h2>
      </div>
      {rows.length === 0 ? (
        <EmptyState title="최근 정산이 없습니다" description="선택 기간에 생성된 정산이 없습니다." />
      ) : (
        <div className="report-record-list">
          {rows.map((row) => (
            <div className="report-record-row" key={row.settlementId}>
              <Link to={`/products/${row.productId}`}>{row.productTitle}</Link>
              <span>주문 #{row.orderId}</span>
              <strong>{formatCurrency(row.amount)}원</strong>
              <StatusBadge status={row.status} />
              <span>{formatDateTime(row.settledAt)}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function StatusDistribution({ title, counts }: { title: string; counts: StatusCount[] }) {
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

function getDefaultPeriod(days: number): SellerPeriodInput {
  const to = new Date();
  const from = new Date();
  from.setDate(to.getDate() - days + 1);

  return {
    from: toDateInputValue(from),
    to: toDateInputValue(to),
  };
}

function validatePeriod(period: SellerPeriodInput) {
  if (!period.from || !period.to) {
    return '시작일과 종료일을 모두 선택해주세요.';
  }

  const from = new Date(`${period.from}T00:00:00`);
  const to = new Date(`${period.to}T00:00:00`);
  const days = Math.floor((to.getTime() - from.getTime()) / 86_400_000) + 1;

  if (days < 1) {
    return '시작일은 종료일보다 늦을 수 없습니다.';
  }

  if (days > 180) {
    return '리포트 기간은 최대 180일까지 조회할 수 있습니다.';
  }

  return null;
}

function toDateInputValue(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}

function formatNumber(value: number) {
  return numberFormatter.format(value);
}

function formatCurrency(value: number) {
  return currencyFormatter.format(value);
}

function formatDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);

  return dateOnlyFormatter.format(new Date(year, month - 1, day));
}

function formatDateTime(value: string) {
  return dateFormatter.format(new Date(value));
}

function sumCounts(counts: StatusCount[]) {
  return counts.reduce((sum, item) => sum + item.count, 0);
}
```

- [ ] **Step 2: Add report page styles**

Append these styles after the existing seller report styles in `web/src/shared/styles.css`:

```css
.report-page-header {
  grid-template-columns: minmax(0, 1fr) auto;
}

.report-filter {
  display: grid;
  gap: 12px;
  border: 1px solid #dfe6ee;
  border-radius: 8px;
  padding: 16px;
  background: #ffffff;
}

.report-filter-fields,
.report-quick-ranges {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: end;
}

.report-filter label {
  display: grid;
  gap: 7px;
  min-width: 160px;
  color: #52616f;
  font-size: 13px;
  font-weight: 800;
}

.report-filter input {
  border: 1px solid #cfd9e3;
  border-radius: 8px;
  padding: 10px 12px;
  background: #fff;
  color: #172026;
}

.report-chip-button {
  border: 1px solid #cfd9e3;
  border-radius: 999px;
  padding: 8px 12px;
  background: #ffffff;
  color: #52616f;
  cursor: pointer;
  font-weight: 800;
}

.daily-sales-list,
.product-ranking-list,
.report-record-list {
  display: grid;
  overflow-x: auto;
  border: 1px solid #dfe6ee;
  border-radius: 8px;
  background: #ffffff;
}

.daily-sales-row,
.product-ranking-row,
.report-record-row {
  display: grid;
  gap: 10px;
  align-items: center;
  min-width: 760px;
  padding: 12px 14px;
  border-bottom: 1px solid #edf2f7;
}

.daily-sales-row {
  grid-template-columns: minmax(120px, 0.8fr) minmax(180px, 1.4fr) minmax(120px, 0.8fr) minmax(72px, 0.4fr);
}

.daily-sales-row:last-child,
.product-ranking-row:last-child,
.report-record-row:last-child {
  border-bottom: 0;
}

.daily-sales-bar-track {
  overflow: hidden;
  height: 10px;
  border-radius: 999px;
  background: #edf2f7;
}

.daily-sales-bar {
  height: 100%;
  border-radius: 999px;
  background: #0f7b78;
}

.product-ranking-row {
  grid-template-columns:
    44px 56px minmax(180px, 1.4fr) minmax(120px, 0.8fr) minmax(72px, 0.4fr)
    minmax(160px, 0.9fr);
}

.ranking-number {
  color: #637282;
  font-weight: 900;
}

.ranking-thumb {
  width: 56px;
  height: 42px;
  border-radius: 8px;
  object-fit: cover;
  background: #e9eef4;
}

.ranking-thumb-fallback {
  display: grid;
  place-items: center;
  color: #637282;
  font-size: 10px;
  font-weight: 900;
  text-align: center;
}

.ranking-title,
.report-record-row a,
.daily-sales-row span,
.daily-sales-row strong,
.product-ranking-row span,
.product-ranking-row strong,
.report-record-row span,
.report-record-row strong {
  min-width: 0;
  overflow-wrap: anywhere;
}

.report-table-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.report-record-row {
  grid-template-columns: minmax(160px, 1.3fr) minmax(100px, 0.8fr) minmax(110px, 0.8fr) minmax(90px, 0.6fr) minmax(150px, 1fr);
}
```

Inside the existing `@media (max-width: 760px)` block, add:

```css
  .report-page-header,
  .report-table-grid {
    grid-template-columns: 1fr;
  }

  .report-filter-fields {
    align-items: stretch;
    flex-direction: column;
  }

  .report-filter .text-button {
    width: 100%;
  }
```

- [ ] **Step 3: Run the web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 4: Commit Task 5**

Run:

```powershell
git add -- web/src/pages/MyReportsPage.tsx web/src/shared/styles.css
git commit -m "feat: expand seller reports page"
```

---

## Task 6: Final Verification And Handoff

**Files:**

- Create: `docs/superpowers/handoffs/2026-06-27-milestone-13-seller-reports-expansion-handoff.md`

- [ ] **Step 1: Run full backend tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: PASS.

- [ ] **Step 2: Run full web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 3: Run diff check**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 4: Verify git status excludes local application yaml from milestone changes**

Run:

```powershell
git status --short --branch --untracked-files=all
```

Expected: milestone files are either committed or intentionally staged for the handoff commit. `backend/src/main/resources/application.yaml` may still appear as a pre-existing local modification and must not be staged.

- [ ] **Step 5: Create the handoff document**

Create `docs/superpowers/handoffs/2026-06-27-milestone-13-seller-reports-expansion-handoff.md`:

```markdown
# Milestone 13 Seller Reports Expansion Handoff

## Completed

- Added `GET /api/seller/reports/period`.
- Added custom period validation with a 180-day maximum range.
- Added period summary metrics.
- Added daily confirmed sales trend with zero-filled missing dates.
- Added top product rankings for confirmed sales.
- Added recent confirmed sales and recent settlement rows.
- Expanded `/me/reports` with date filters, quick ranges, ranking, trend, and recent records.

## Verification

- Backend tests: `.\gradlew.bat test`
- Web build: `npm run build`
- Diff check: `git diff --check`

## Local Notes

- Do not include `backend/src/main/resources/application.yaml`; it is a pre-existing local-only development change.
- The existing dashboard API remains unchanged.

## Follow-Up Candidates

- Add CSV export after report semantics settle.
- Add frontend regression tests if test infrastructure is introduced.
- Revisit charting only if compact bars become insufficient.
- Add product image upload and product UX improvements in a later product-focused milestone.
```

- [ ] **Step 6: Commit handoff**

Run:

```powershell
git add -- docs/superpowers/handoffs/2026-06-27-milestone-13-seller-reports-expansion-handoff.md
git commit -m "docs: add milestone 13 seller reports handoff"
```

Do not stage `backend/src/main/resources/application.yaml`.

- [ ] **Step 7: Final status check**

Run:

```powershell
git status --short --branch
```

Expected: branch is ahead by the milestone commits and only the pre-existing `backend/src/main/resources/application.yaml` local change remains unstaged.

---

## Self-Review

- Spec coverage: The plan implements the period API, custom date range, summary metrics, product rankings, daily trend, recent sales, recent settlements, frontend filters, frontend operational sections, validation errors, seller scoping, and verification expectations from the spec.
- Placeholder scan: The plan contains no deferred-work markers or unspecified implementation steps.
- Scope check: Product image upload, wishlist, cart, reviews, cancellation/refund, exports, admin reports, caching, and external analytics are intentionally excluded.
- Type consistency: Backend response record names match frontend type names by field shape. Query method names match the service calls shown in the plan.
- Existing local state: The plan repeatedly excludes `backend/src/main/resources/application.yaml` from staging.
