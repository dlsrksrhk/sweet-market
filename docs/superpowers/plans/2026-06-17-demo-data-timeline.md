# Demo Data Timeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the tiny local/dev demo seed with a deterministic 180-day timeline that makes admin operations, settlement operations, and automatic confirmation demos feel substantial.

**Architecture:** Keep the existing `DemoDataInitializer` entry point and `admin@example.com` idempotency guard. Generate members, products, orders, payments, deliveries, and settlements through domain methods first, then use `JdbcTemplate` parameterized updates for historical timestamps and the small set of demo-only settlement status variants.

**Tech Stack:** Spring Boot, Spring Data JPA, PostgreSQL/Testcontainers, `JdbcTemplate`, JUnit 5, AssertJ, Java 21.

---

## File Structure

- Modify: `backend/src/main/java/com/sweet/market/demo/DemoDataInitializer.java`
  - Keep the `ApplicationRunner` and seed guard.
  - Add `JdbcTemplate` injection for deterministic timestamp/status backfills.
  - Replace the six hand-written demo scenarios with deterministic account, product, order, delivery, payment, and settlement generation.
  - Keep helper records private to this class unless the file becomes hard to read during implementation.

- Modify: `backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java`
  - Update constructor calls for the new `JdbcTemplate` dependency.
  - Expand assertions for counts, status coverage, 180-day date range, automatic confirmation candidates, settled/unsettled confirmed orders, and settlement status distribution.
  - Keep all `@Test` method names in Korean_with_underscores.

- Do not modify: `backend/src/main/resources/application.yaml`
  - It has local-only changes in this workspace.

- Do not modify web files for this feature.

---

### Task 1: Expand Demo Initializer Test Coverage

**Files:**
- Modify: `backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java`
- Read-only context: `docs/superpowers/specs/2026-06-17-demo-data-timeline-design.md`

- [ ] **Step 1: Replace the test imports**

Use these imports at the top of `DemoDataInitializerTest.java`:

```java
package com.sweet.market.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.domain.MemberRole;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.repository.PaymentRepository;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.settlement.repository.SettlementRepository;
import com.sweet.market.support.IntegrationTestSupport;
```

- [ ] **Step 2: Update the initializer construction in the test**

Replace the current initializer construction with this helper method:

```java
private DemoDataInitializer initializer() {
    return new DemoDataInitializer(
            memberRepository,
            productRepository,
            orderRepository,
            paymentRepository,
            deliveryRepository,
            settlementRepository,
            passwordEncoder,
            jdbcTemplate
    );
}
```

- [ ] **Step 3: Replace the idempotency test body**

Replace `반복_실행해도_데모_데이터를_중복_생성하지_않는다` with:

```java
@Test
void 반복_실행해도_데모_데이터를_중복_생성하지_않는다() {
    DemoDataInitializer initializer = initializer();

    transactionTemplate.executeWithoutResult(status -> initializer.run());
    Counts firstCounts = counts();

    transactionTemplate.executeWithoutResult(status -> initializer.run());
    Counts secondCounts = counts();

    assertThat(secondCounts).isEqualTo(firstCounts);
    assertThat(firstCounts.members()).isGreaterThanOrEqualTo(35);
    assertThat(firstCounts.products()).isGreaterThanOrEqualTo(120);
    assertThat(firstCounts.orders()).isGreaterThanOrEqualTo(240);
    assertThat(firstCounts.payments()).isGreaterThanOrEqualTo(190);
    assertThat(firstCounts.deliveries()).isGreaterThanOrEqualTo(150);
    assertThat(firstCounts.settlements()).isGreaterThanOrEqualTo(70);

    assertDemoAccount("admin@example.com", MemberRole.ADMIN);
    for (String email : List.of(
            "seller1@example.com",
            "seller2@example.com",
            "seller10@example.com",
            "buyer1@example.com",
            "buyer2@example.com",
            "buyer24@example.com"
    )) {
        assertDemoAccount(email, MemberRole.MEMBER);
    }
}
```

- [ ] **Step 4: Add status coverage test**

Add this test method:

```java
@Test
void 데모_데이터는_관리자_필터에_필요한_상태들을_모두_포함한다() {
    DemoDataInitializer initializer = initializer();

    transactionTemplate.executeWithoutResult(status -> initializer.run());

    assertStatuses("products", "ON_SALE", "RESERVED", "SOLD_OUT", "HIDDEN");
    assertStatuses("orders", "CREATED", "PAID", "SHIPPING", "DELIVERED", "CONFIRMED", "CANCELED");
    assertStatuses("payments", "APPROVED", "CANCELED");
    assertStatuses("deliveries", "SHIPPING", "DELIVERED");
    assertStatuses("settlements", "COMPLETED", "READY", "FAILED");
}
```

- [ ] **Step 5: Add timeline distribution test**

Add this test method:

```java
@Test
void 데모_주문은_최근_180일_범위에_분포한다() {
    DemoDataInitializer initializer = initializer();

    transactionTemplate.executeWithoutResult(status -> initializer.run());

    LocalDate today = LocalDate.now();
    LocalDateTime minOrderedAt = jdbcTemplate.queryForObject(
            "select min(ordered_at) from orders",
            LocalDateTime.class
    );
    LocalDateTime maxOrderedAt = jdbcTemplate.queryForObject(
            "select max(ordered_at) from orders",
            LocalDateTime.class
    );
    Long distinctOrderDays = jdbcTemplate.queryForObject(
            "select count(distinct cast(ordered_at as date)) from orders",
            Long.class
    );

    assertThat(minOrderedAt).isNotNull();
    assertThat(maxOrderedAt).isNotNull();
    assertThat(minOrderedAt.toLocalDate()).isAfterOrEqualTo(today.minusDays(180));
    assertThat(maxOrderedAt.toLocalDate()).isBeforeOrEqualTo(today);
    assertThat(distinctOrderDays).isGreaterThanOrEqualTo(90);
}
```

- [ ] **Step 6: Add operational scenario test**

Add this test method:

```java
@Test
void 데모_데이터는_정산과_자동구매확정_시나리오를_포함한다() {
    DemoDataInitializer initializer = initializer();

    transactionTemplate.executeWithoutResult(status -> initializer.run());

    assertThat(deliveredAutoConfirmCandidateCount()).isGreaterThanOrEqualTo(10);
    assertThat(confirmedSettledOrderCount()).isGreaterThanOrEqualTo(60);
    assertThat(confirmedUnsettledEligibleOrderCount()).isGreaterThanOrEqualTo(20);
    assertThat(confirmedRecentUnsettledOrderCount()).isGreaterThanOrEqualTo(8);
    assertThat(settlementStatusCount("COMPLETED")).isGreaterThanOrEqualTo(60);
    assertThat(settlementStatusCount("READY")).isBetween(1L, 5L);
    assertThat(settlementStatusCount("FAILED")).isBetween(1L, 5L);
}
```

- [ ] **Step 7: Replace and add SQL helper methods**

Keep `counts()` and `assertDemoAccount(...)`, then replace the old count helpers with:

```java
private void assertStatuses(String tableName, String... expectedStatuses) {
    List<String> statuses = jdbcTemplate.queryForList(
            "select distinct status from " + tableName,
            String.class
    );

    assertThat(statuses).contains(expectedStatuses);
}

private Long deliveredAutoConfirmCandidateCount() {
    return jdbcTemplate.queryForObject("""
            select count(*)
            from deliveries d
            join orders o on o.id = d.order_id
            where d.status = 'DELIVERED'
              and o.status = 'DELIVERED'
              and d.completed_at < ?
            """, Long.class, LocalDateTime.now().minusDays(7));
}

private Long confirmedSettledOrderCount() {
    return jdbcTemplate.queryForObject("""
            select count(*)
            from orders o
            join settlements s on s.order_id = o.id
            where o.status = 'CONFIRMED'
            """, Long.class);
}

private Long confirmedUnsettledEligibleOrderCount() {
    return jdbcTemplate.queryForObject("""
            select count(*)
            from orders o
            left join settlements s on s.order_id = o.id
            where o.status = 'CONFIRMED'
              and o.confirmed_at < ?
              and s.id is null
            """, Long.class, LocalDateTime.now().minusDays(7));
}

private Long confirmedRecentUnsettledOrderCount() {
    return jdbcTemplate.queryForObject("""
            select count(*)
            from orders o
            left join settlements s on s.order_id = o.id
            where o.status = 'CONFIRMED'
              and o.confirmed_at >= ?
              and s.id is null
            """, Long.class, LocalDateTime.now().minusDays(7));
}

private Long settlementStatusCount(String status) {
    return jdbcTemplate.queryForObject("""
            select count(*)
            from settlements
            where status = ?
            """, Long.class, status);
}
```

- [ ] **Step 8: Run the focused test and verify failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.demo.DemoDataInitializerTest
```

Expected: FAIL at compile time because `DemoDataInitializer` does not yet accept `JdbcTemplate`, or fail at assertion time because the seed is still too small.

Do not commit this failing task by itself.

---

### Task 2: Add Initializer Wiring and Deterministic Seed Constants

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/demo/DemoDataInitializer.java`
- Test: `backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java`

- [ ] **Step 1: Add imports**

Add these imports to `DemoDataInitializer.java`:

```java
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
```

- [ ] **Step 2: Add constants and `JdbcTemplate` field**

Place these fields below `DEMO_PASSWORD`:

```java
private static final int SELLER_COUNT = 10;
private static final int BUYER_COUNT = 24;
private static final int CATALOG_PRODUCT_COUNT = 120;
private static final int CREATED_ORDER_COUNT = 25;
private static final int PAID_ORDER_COUNT = 30;
private static final int SHIPPING_ORDER_COUNT = 30;
private static final int DELIVERED_OLD_ORDER_COUNT = 25;
private static final int DELIVERED_RECENT_ORDER_COUNT = 15;
private static final int CONFIRMED_SETTLED_ORDER_COUNT = 70;
private static final int CONFIRMED_UNSETTLED_ELIGIBLE_ORDER_COUNT = 25;
private static final int CONFIRMED_UNSETTLED_RECENT_ORDER_COUNT = 10;
private static final int CANCELED_CREATED_ORDER_COUNT = 10;
private static final int CANCELED_PAID_ORDER_COUNT = 10;
private static final int ORDER_SPAN_DAYS = 180;

private static final List<String> PRODUCT_GROUPS = List.of(
        "Electronics",
        "Kitchen",
        "Fashion",
        "Books",
        "Sports",
        "Home",
        "Hobby",
        "Office"
);
```

Add this dependency field:

```java
private final JdbcTemplate jdbcTemplate;
```

- [ ] **Step 3: Update constructor signature and assignment**

Change the constructor to:

```java
public DemoDataInitializer(
        MemberRepository memberRepository,
        ProductRepository productRepository,
        OrderRepository orderRepository,
        PaymentRepository paymentRepository,
        DeliveryRepository deliveryRepository,
        SettlementRepository settlementRepository,
        PasswordEncoder passwordEncoder,
        JdbcTemplate jdbcTemplate
) {
    this.memberRepository = memberRepository;
    this.productRepository = productRepository;
    this.orderRepository = orderRepository;
    this.paymentRepository = paymentRepository;
    this.deliveryRepository = deliveryRepository;
    this.settlementRepository = settlementRepository;
    this.passwordEncoder = passwordEncoder;
    this.jdbcTemplate = jdbcTemplate;
}
```

- [ ] **Step 4: Replace `run()` with high-level orchestration**

Replace the current `run()` method body with:

```java
@Transactional
public void run() {
    if (memberRepository.existsByEmail("admin@example.com")) {
        return;
    }

    LocalDate today = LocalDate.now();
    String encodedPassword = passwordEncoder.encode(DEMO_PASSWORD);

    memberRepository.save(Member.createAdmin("admin@example.com", encodedPassword, "admin"));
    List<Member> sellers = createSellers(encodedPassword);
    List<Member> buyers = createBuyers(encodedPassword);

    createCatalogProducts(sellers);
    createOrderTimeline(sellers, buyers, today);
}
```

- [ ] **Step 5: Add account generation helpers**

Add these methods near the top of the helper section:

```java
private List<Member> createSellers(String encodedPassword) {
    List<Member> sellers = new ArrayList<>();
    for (int index = 1; index <= SELLER_COUNT; index++) {
        sellers.add(memberRepository.save(Member.create(
                "seller" + index + "@example.com",
                encodedPassword,
                "seller" + index
        )));
    }
    return sellers;
}

private List<Member> createBuyers(String encodedPassword) {
    List<Member> buyers = new ArrayList<>();
    for (int index = 1; index <= BUYER_COUNT; index++) {
        buyers.add(memberRepository.save(Member.create(
                "buyer" + index + "@example.com",
                encodedPassword,
                "buyer" + index
        )));
    }
    return buyers;
}
```

- [ ] **Step 6: Add temporary stubs so the file compiles**

Add these methods. They will be replaced in later tasks:

```java
private void createCatalogProducts(List<Member> sellers) {
}

private void createOrderTimeline(List<Member> sellers, List<Member> buyers, LocalDate today) {
}
```

- [ ] **Step 7: Run focused test and verify expected assertion failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.demo.DemoDataInitializerTest
```

Expected: FAIL because the initializer now creates accounts but not enough products, orders, payments, deliveries, or settlements.

Do not commit yet.

---

### Task 3: Generate Product Catalog Coverage

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/demo/DemoDataInitializer.java`
- Test: `backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java`

- [ ] **Step 1: Replace `createCatalogProducts`**

Replace the temporary method with:

```java
private void createCatalogProducts(List<Member> sellers) {
    for (int index = 1; index <= CATALOG_PRODUCT_COUNT; index++) {
        Member seller = pick(sellers, index);
        String group = PRODUCT_GROUPS.get((index - 1) % PRODUCT_GROUPS.size());
        Product product = productRepository.save(Product.create(
                seller,
                group + " Demo Item " + index,
                "Local demo " + group.toLowerCase() + " listing number " + index + ".",
                priceFor(index)
        ));

        if (index % 12 == 0) {
            product.hide();
        }
    }
}
```

- [ ] **Step 2: Add shared helper methods**

Add these helpers below the product method:

```java
private Member pick(List<Member> members, int oneBasedIndex) {
    return members.get((oneBasedIndex - 1) % members.size());
}

private long priceFor(int index) {
    return 8_000L + (long) (index % 37) * 3_000L;
}

private LocalDateTime atNoon(LocalDate date) {
    return date.atTime(12, 0);
}
```

- [ ] **Step 3: Run focused test and verify product assertions improve**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.demo.DemoDataInitializerTest
```

Expected: FAIL because order/payment/delivery/settlement counts are still missing, but product count and `ON_SALE`/`HIDDEN` product statuses should no longer be the blocker.

Do not commit yet.

---

### Task 4: Generate Order, Payment, Delivery, and Timestamp Timeline

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/demo/DemoDataInitializer.java`
- Test: `backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java`

- [ ] **Step 1: Add private scenario records**

Add these records near the bottom of `DemoDataInitializer.java`, above the final closing brace:

```java
private record OrderScenario(
        DemoOrderStatus status,
        int count,
        int startOffsetDays,
        boolean oldDeliveredCandidate
) {
}

private enum DemoOrderStatus {
    CREATED,
    PAID,
    SHIPPING,
    DELIVERED,
    CONFIRMED,
    CANCELED_CREATED,
    CANCELED_PAID
}
```

- [ ] **Step 2: Replace `createOrderTimeline`**

Replace the temporary method with:

```java
private void createOrderTimeline(List<Member> sellers, List<Member> buyers, LocalDate today) {
    List<OrderScenario> scenarios = List.of(
            new OrderScenario(DemoOrderStatus.CREATED, CREATED_ORDER_COUNT, 179, false),
            new OrderScenario(DemoOrderStatus.PAID, PAID_ORDER_COUNT, 154, false),
            new OrderScenario(DemoOrderStatus.SHIPPING, SHIPPING_ORDER_COUNT, 129, false),
            new OrderScenario(DemoOrderStatus.DELIVERED, DELIVERED_OLD_ORDER_COUNT, 104, true),
            new OrderScenario(DemoOrderStatus.DELIVERED, DELIVERED_RECENT_ORDER_COUNT, 12, false),
            new OrderScenario(DemoOrderStatus.CONFIRMED, CONFIRMED_SETTLED_ORDER_COUNT, 96, false),
            new OrderScenario(DemoOrderStatus.CONFIRMED, CONFIRMED_UNSETTLED_ELIGIBLE_ORDER_COUNT, 46, false),
            new OrderScenario(DemoOrderStatus.CONFIRMED, CONFIRMED_UNSETTLED_RECENT_ORDER_COUNT, 13, false),
            new OrderScenario(DemoOrderStatus.CANCELED_CREATED, CANCELED_CREATED_ORDER_COUNT, 33, false),
            new OrderScenario(DemoOrderStatus.CANCELED_PAID, CANCELED_PAID_ORDER_COUNT, 24, false)
    );

    int sequence = 1;
    for (OrderScenario scenario : scenarios) {
        for (int index = 0; index < scenario.count(); index++) {
            int offsetDays = Math.max(0, scenario.startOffsetDays() - index);
            LocalDate orderDate = today.minusDays(offsetDays);
            createScenarioOrder(sellers, buyers, scenario, sequence, orderDate);
            sequence++;
        }
    }
}
```

- [ ] **Step 3: Add scenario order creator**

Add this method:

```java
private void createScenarioOrder(
        List<Member> sellers,
        List<Member> buyers,
        OrderScenario scenario,
        int sequence,
        LocalDate orderDate
) {
    Member seller = pick(sellers, sequence);
    Member buyer = pick(buyers, sequence * 3);
    Product product = createProduct(
            seller,
            "Timeline " + scenario.status().name() + " Product " + sequence,
            priceFor(sequence + CATALOG_PRODUCT_COUNT)
    );
    Order order = orderRepository.save(Order.create(buyer, product));
    LocalDateTime orderedAt = orderDate.atTime(9 + sequence % 8, 15);
    updateOrderOrderedAt(order, orderedAt);

    switch (scenario.status()) {
        case CREATED -> {
        }
        case PAID -> createApprovedPayment(order, sequence, orderedAt.plusHours(1));
        case SHIPPING -> {
            createApprovedPayment(order, sequence, orderedAt.plusHours(1));
            createShippingDelivery(order, sequence, orderedAt.plusDays(1));
        }
        case DELIVERED -> {
            createApprovedPayment(order, sequence, orderedAt.plusHours(1));
            Delivery delivery = createShippingDelivery(order, sequence, orderedAt.plusDays(1));
            delivery.complete();
            LocalDateTime completedAt = scenario.oldDeliveredCandidate()
                    ? orderedAt.plusDays(3)
                    : LocalDateTime.now().minusDays(sequence % 5);
            updateDeliveredTimestamps(delivery, orderedAt.plusDays(1), completedAt);
        }
        case CONFIRMED -> {
            createApprovedPayment(order, sequence, orderedAt.plusHours(1));
            Delivery delivery = createShippingDelivery(order, sequence, orderedAt.plusDays(1));
            delivery.complete();
            LocalDateTime completedAt = orderedAt.plusDays(3);
            order.confirm();
            updateDeliveredTimestamps(delivery, orderedAt.plusDays(1), completedAt);
            updateOrderConfirmedAt(order, completedAt.plusDays(1));
        }
        case CANCELED_CREATED -> {
            order.cancel();
            updateOrderCanceledAt(order, orderedAt.plusHours(2));
        }
        case CANCELED_PAID -> {
            Payment payment = createApprovedPayment(order, sequence, orderedAt.plusHours(1));
            payment.cancel();
            updatePaymentCanceledAt(payment, orderedAt.plusHours(3));
            updateOrderCanceledAt(order, orderedAt.plusHours(3));
        }
    }
}
```

- [ ] **Step 4: Add payment and delivery helpers**

Add these methods:

```java
private Payment createApprovedPayment(Order order, int sequence, LocalDateTime approvedAt) {
    Payment payment = paymentRepository.save(Payment.approve(order, "demo-payment-" + sequence));
    updatePaymentApprovedAt(payment, approvedAt);
    return payment;
}

private Delivery createShippingDelivery(Order order, int sequence, LocalDateTime startedAt) {
    Delivery delivery = deliveryRepository.save(Delivery.start(order, "DEMO-TRACK-" + sequence));
    updateDeliveryStartedAt(delivery, startedAt);
    return delivery;
}
```

- [ ] **Step 5: Add timestamp backfill helpers**

Add these methods:

```java
private void updateOrderOrderedAt(Order order, LocalDateTime orderedAt) {
    jdbcTemplate.update("update orders set ordered_at = ? where id = ?", orderedAt, order.getId());
}

private void updateOrderCanceledAt(Order order, LocalDateTime canceledAt) {
    jdbcTemplate.update("update orders set canceled_at = ? where id = ?", canceledAt, order.getId());
}

private void updateOrderConfirmedAt(Order order, LocalDateTime confirmedAt) {
    jdbcTemplate.update("update orders set confirmed_at = ? where id = ?", confirmedAt, order.getId());
}

private void updatePaymentApprovedAt(Payment payment, LocalDateTime approvedAt) {
    jdbcTemplate.update("update payments set approved_at = ? where id = ?", approvedAt, payment.getId());
}

private void updatePaymentCanceledAt(Payment payment, LocalDateTime canceledAt) {
    jdbcTemplate.update("update payments set canceled_at = ? where id = ?", canceledAt, payment.getId());
}

private void updateDeliveryStartedAt(Delivery delivery, LocalDateTime startedAt) {
    jdbcTemplate.update("update deliveries set started_at = ? where id = ?", startedAt, delivery.getId());
}

private void updateDeliveredTimestamps(Delivery delivery, LocalDateTime startedAt, LocalDateTime completedAt) {
    jdbcTemplate.update(
            "update deliveries set started_at = ?, completed_at = ? where id = ?",
            startedAt,
            completedAt,
            delivery.getId()
    );
}
```

- [ ] **Step 6: Run focused test and verify remaining settlement failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.demo.DemoDataInitializerTest
```

Expected: FAIL because settlement counts/statuses are still missing. Member, product, order, payment, delivery, and timeline assertions should now be close to passing.

Do not commit yet.

---

### Task 5: Generate Settlement History and Exceptional Settlement Statuses

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/demo/DemoDataInitializer.java`
- Test: `backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java`

- [ ] **Step 1: Call settlement creation from confirmed scenarios**

In the `CONFIRMED` branch of `createScenarioOrder`, after `updateOrderConfirmedAt(order, completedAt.plusDays(1));`, add:

```java
if (sequence <= CREATED_ORDER_COUNT
        + PAID_ORDER_COUNT
        + SHIPPING_ORDER_COUNT
        + DELIVERED_OLD_ORDER_COUNT
        + DELIVERED_RECENT_ORDER_COUNT
        + CONFIRMED_SETTLED_ORDER_COUNT) {
    Settlement settlement = settlementRepository.save(Settlement.create(order));
    updateSettlementSettledAt(settlement, completedAt.plusDays(3));
    updateExceptionalSettlementStatus(settlement, sequence);
}
```

- [ ] **Step 2: Add settlement timestamp and status helpers**

Add these methods:

```java
private void updateSettlementSettledAt(Settlement settlement, LocalDateTime settledAt) {
    jdbcTemplate.update("update settlements set settled_at = ? where id = ?", settledAt, settlement.getId());
}

private void updateExceptionalSettlementStatus(Settlement settlement, int sequence) {
    if (sequence % 35 == 0) {
        jdbcTemplate.update("update settlements set status = 'READY' where id = ?", settlement.getId());
    } else if (sequence % 37 == 0) {
        jdbcTemplate.update("update settlements set status = 'FAILED' where id = ?", settlement.getId());
    }
}
```

- [ ] **Step 3: Run focused test and verify it passes**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.demo.DemoDataInitializerTest
```

Expected: PASS.

- [ ] **Step 4: Commit backend demo data implementation**

Stage only backend initializer and demo initializer test:

```powershell
git add -- backend/src/main/java/com/sweet/market/demo/DemoDataInitializer.java backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java
git commit -m "feat: expand local demo data timeline"
```

Expected: commit succeeds. Do not stage `backend/src/main/resources/application.yaml` or `web/src/shared/styles.css`.

---

### Task 6: Full Backend Verification and Cleanup

**Files:**
- Verify: `backend/src/main/java/com/sweet/market/demo/DemoDataInitializer.java`
- Verify: `backend/src/test/java/com/sweet/market/demo/DemoDataInitializerTest.java`

- [ ] **Step 1: Run full backend test suite**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: PASS.

- [ ] **Step 2: Run diff whitespace check**

Run from repository root:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 3: Confirm working tree scope**

Run:

```powershell
git status --short --branch --untracked-files=all
```

Expected: implementation commit is present, and unrelated pre-existing local changes may still show:

```text
 M backend/src/main/resources/application.yaml
 M web/src/shared/styles.css
```

If `DemoDataInitializer.java` or `DemoDataInitializerTest.java` still appears modified after the implementation commit, inspect the diff and either commit the intended remaining change or fix the missed staging before handing off.

- [ ] **Step 4: Optional local demo smoke run**

Run only if the developer wants to manually inspect the local UI with reseeded tables:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat bootRun --args='--spring.profiles.active=local'
```

Expected: app starts, and logs do not show seed exceptions. Stop the process after confirming startup.

Do not commit for this task unless a verification-only fix was required.

---

## Self-Review

- Spec coverage: The plan keeps the existing seed guard, generates deterministic local/dev data, covers 180 days, includes all required statuses, creates settlement and automatic confirmation scenarios, and avoids web changes.
- Safety: The plan does not add table deletion, reset endpoints, or startup truncation.
- Type consistency: The plan uses current domain methods `Order.create`, `Payment.approve`, `Delivery.start`, `Delivery.complete`, `Order.confirm`, `Order.cancel`, `Payment.cancel`, `Product.hide`, and `Settlement.create`.
- SQL scope: Direct SQL is limited to timestamp backfills and small settlement status adjustments through `JdbcTemplate` parameter binding.
- Test naming: All new `@Test` methods use Korean_with_underscores.
