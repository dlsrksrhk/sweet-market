# Milestone 9 Automatic Purchase Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add automatic purchase confirmation for delivered orders older than 7 days, with a local/dev scheduler and an admin web trigger.

**Architecture:** A small transactional order application service selects delivered deliveries older than the threshold, calls the existing `Order.confirm()` domain method, and returns a count-based result. The scheduler and admin API both call that same service; the web admin screen adds a compact manual trigger panel.

**Tech Stack:** Spring Boot 3.5, Spring Data JPA, Spring Scheduling, Spring Security, JUnit 5, MockMvc, Testcontainers PostgreSQL, Vite React TypeScript, TanStack Query.

---

## File Structure

- Modify: `backend/src/main/java/com/sweet/market/MarketApplication.java`
  - Enable configuration property scanning for `@ConfigurationProperties`.
- Create: `backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmProperties.java`
  - Holds `thresholdDays` and `limit` defaults.
- Create: `backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmResult.java`
  - Internal result returned by the service and scheduler.
- Create: `backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmService.java`
  - Transactional use case that confirms eligible delivered orders.
- Modify: `backend/src/main/java/com/sweet/market/delivery/repository/DeliveryRepository.java`
  - Adds the eligible delivery query.
- Create: `backend/src/main/java/com/sweet/market/order/api/OrderAutoConfirmResponse.java`
  - API response DTO for the admin trigger.
- Create: `backend/src/main/java/com/sweet/market/order/api/AdminOrderAutoConfirmController.java`
  - Admin endpoint `POST /api/admin/orders/auto-confirm`.
- Create: `backend/src/main/java/com/sweet/market/order/scheduler/OrderAutoConfirmSchedulingConfig.java`
  - Enables scheduling only for `local` and `dev`.
- Create: `backend/src/main/java/com/sweet/market/order/scheduler/OrderAutoConfirmScheduler.java`
  - Scheduled entry point, guarded by `market.order.auto-confirm.enabled`.
- Create: `backend/src/test/java/com/sweet/market/order/OrderAutoConfirmServiceTest.java`
  - Service-level integration tests.
- Create: `backend/src/test/java/com/sweet/market/order/AdminOrderAutoConfirmApiTest.java`
  - Admin trigger and security tests.
- Modify: `web/src/features/admin/adminBatchApi.ts`
  - Adds auto-confirm result type and API function.
- Modify: `web/src/pages/AdminSettlementBatchPage.tsx`
  - Adds the compact manual trigger panel.

Do not overwrite unrelated local changes in `backend/src/main/resources/application.yaml`.

---

### Task 1: Backend Automatic Confirmation Service

**Files:**
- Create: `backend/src/test/java/com/sweet/market/order/OrderAutoConfirmServiceTest.java`
- Create: `backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmProperties.java`
- Create: `backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmResult.java`
- Create: `backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmService.java`
- Modify: `backend/src/main/java/com/sweet/market/delivery/repository/DeliveryRepository.java`
- Modify: `backend/src/main/java/com/sweet/market/MarketApplication.java`

- [ ] **Step 1: Write failing service tests**

Create `backend/src/test/java/com/sweet/market/order/OrderAutoConfirmServiceTest.java`:

```java
package com.sweet.market.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.order.application.OrderAutoConfirmResult;
import com.sweet.market.order.application.OrderAutoConfirmService;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

@TestPropertySource(properties = {
        "spring.batch.job.enabled=false",
        "market.order.auto-confirm.threshold-days=7",
        "market.order.auto-confirm.limit=100"
})
class OrderAutoConfirmServiceTest extends IntegrationTestSupport {

    @Autowired
    private OrderAutoConfirmService orderAutoConfirmService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 배송완료_7일이_지난_주문은_자동_구매확정된다() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 10, 0);
        Long orderId = createDeliveredOrder("old", now.minusDays(8));

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(now);

        Order order = orderRepository.findWithBuyerAndProductById(orderId).orElseThrow();
        assertThat(result.confirmedCount()).isEqualTo(1);
        assertThat(result.deliveredBefore()).isEqualTo(now.minusDays(7));
        assertThat(result.thresholdDays()).isEqualTo(7);
        assertThat(result.executedAt()).isEqualTo(now);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isNotNull();
        assertThat(order.getProduct().getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    void 배송완료_7일이_지나지_않은_주문은_자동_구매확정하지_않는다() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 10, 0);
        Long orderId = createDeliveredOrder("recent", now.minusDays(6));

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(now);

        Order order = orderRepository.findWithBuyerAndProductById(orderId).orElseThrow();
        assertThat(result.confirmedCount()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getConfirmedAt()).isNull();
        assertThat(order.getProduct().getStatus()).isEqualTo(ProductStatus.RESERVED);
    }

    @Test
    void 이미_구매확정된_주문은_자동_구매확정에서_제외한다() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 10, 0);
        Long orderId = createDeliveredOrder("confirmed", now.minusDays(8));
        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findWithBuyerAndProductById(orderId).orElseThrow();
            order.confirm();
        });

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(now);

        assertThat(result.confirmedCount()).isZero();
        assertThat(orderRepository.findWithBuyerAndProductById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void 배송중_주문은_자동_구매확정에서_제외한다() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 10, 0);
        Long orderId = createShippingOrder("shipping");

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(now);

        assertThat(result.confirmedCount()).isZero();
        assertThat(orderRepository.findWithBuyerAndProductById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.SHIPPING);
    }

    @Test
    void 결제완료_주문은_자동_구매확정에서_제외한다() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 10, 0);
        Long orderId = createPaidOrder("paid");

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(now);

        assertThat(result.confirmedCount()).isZero();
        assertThat(orderRepository.findWithBuyerAndProductById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.PAID);
    }

    @Test
    void 자동_구매확정은_반복_실행해도_중복_처리하지_않는다() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 10, 0);
        Long orderId = createDeliveredOrder("idempotent", now.minusDays(8));

        OrderAutoConfirmResult firstResult = orderAutoConfirmService.confirmDeliveredOrders(now);
        OrderAutoConfirmResult secondResult = orderAutoConfirmService.confirmDeliveredOrders(now.plusMinutes(1));

        Order order = orderRepository.findWithBuyerAndProductById(orderId).orElseThrow();
        assertThat(firstResult.confirmedCount()).isEqualTo(1);
        assertThat(secondResult.confirmedCount()).isZero();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getProduct().getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    void 설정된_처리_한도까지만_자동_구매확정한다() {
        LocalDateTime now = LocalDateTime.of(2026, 6, 15, 10, 0);
        createDeliveredOrder("limit-1", now.minusDays(8));
        createDeliveredOrder("limit-2", now.minusDays(8));

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(now);

        assertThat(result.confirmedCount()).isEqualTo(2);
    }

    private Long createDeliveredOrder(String suffix, LocalDateTime completedAt) {
        Long deliveryId = createDelivery(suffix, true).deliveryId();
        jdbcTemplate.update(
                "update deliveries set completed_at = ? where id = ?",
                Timestamp.valueOf(completedAt),
                deliveryId
        );
        return deliveryRepository.findById(deliveryId).orElseThrow().getOrder().getId();
    }

    private Long createShippingOrder(String suffix) {
        return createDelivery(suffix, false).orderId();
    }

    private Long createPaidOrder(String suffix) {
        return transactionTemplate.execute(status -> {
            Order order = createBaseOrder(suffix);
            order.markPaid();
            entityManager.persist(order);
            return order.getId();
        });
    }

    private CreatedDelivery createDelivery(String suffix, boolean complete) {
        return transactionTemplate.execute(status -> {
            Order order = createBaseOrder(suffix);
            order.markPaid();
            Delivery delivery = Delivery.start(order, "TRACK-" + suffix);
            if (complete) {
                delivery.complete();
            }
            entityManager.persist(order);
            entityManager.persist(delivery);
            entityManager.flush();
            return new CreatedDelivery(order.getId(), delivery.getId());
        });
    }

    private Order createBaseOrder(String suffix) {
        Member seller = Member.create("seller-auto-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
        Member buyer = Member.create("buyer-auto-" + suffix + "@example.com", "encoded-password", "buyer-" + suffix);
        entityManager.persist(seller);
        entityManager.persist(buyer);

        Product product = Product.create(seller, "Auto Confirm Product " + suffix, "description", 10_000L);
        entityManager.persist(product);

        return Order.create(buyer, product);
    }

    private record CreatedDelivery(Long orderId, Long deliveryId) {
    }
}
```

- [ ] **Step 2: Run the service test and verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.order.OrderAutoConfirmServiceTest
```

Expected: compilation fails because `OrderAutoConfirmService` and related types do not exist.

- [ ] **Step 3: Add configuration properties and result type**

Modify `backend/src/main/java/com/sweet/market/MarketApplication.java`:

```java
package com.sweet.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class MarketApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketApplication.class, args);
    }

}
```

Create `backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmProperties.java`:

```java
package com.sweet.market.order.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "market.order.auto-confirm")
public record OrderAutoConfirmProperties(
        @DefaultValue("7") int thresholdDays,
        @DefaultValue("100") int limit
) {

    public OrderAutoConfirmProperties {
        if (thresholdDays < 1) {
            throw new IllegalArgumentException("thresholdDays must be positive");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
```

Create `backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmResult.java`:

```java
package com.sweet.market.order.application;

import java.time.LocalDateTime;

public record OrderAutoConfirmResult(
        int confirmedCount,
        LocalDateTime deliveredBefore,
        int thresholdDays,
        LocalDateTime executedAt
) {
}
```

- [ ] **Step 4: Add the eligible delivery query**

Modify `backend/src/main/java/com/sweet/market/delivery/repository/DeliveryRepository.java`:

```java
package com.sweet.market.delivery.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.domain.DeliveryStatus;
import com.sweet.market.order.domain.OrderStatus;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "order.product.images"})
    Optional<Delivery> findWithOrderByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.product"})
    @Query("""
            select d
            from Delivery d
            where d.status = :deliveryStatus
              and d.order.status = :orderStatus
              and d.completedAt < :completedBefore
            order by d.id asc
            """)
    List<Delivery> findAutoConfirmCandidates(
            @Param("completedBefore") LocalDateTime completedBefore,
            @Param("deliveryStatus") DeliveryStatus deliveryStatus,
            @Param("orderStatus") OrderStatus orderStatus,
            Pageable pageable
    );
}
```

- [ ] **Step 5: Add the automatic confirmation service**

Create `backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmService.java`:

```java
package com.sweet.market.order.application;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.domain.DeliveryStatus;
import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;

@Service
public class OrderAutoConfirmService {

    private final DeliveryRepository deliveryRepository;
    private final OrderAutoConfirmProperties properties;

    public OrderAutoConfirmService(
            DeliveryRepository deliveryRepository,
            OrderAutoConfirmProperties properties
    ) {
        this.deliveryRepository = deliveryRepository;
        this.properties = properties;
    }

    @Transactional
    public OrderAutoConfirmResult confirmDeliveredOrders() {
        return confirmDeliveredOrders(LocalDateTime.now());
    }

    @Transactional
    public OrderAutoConfirmResult confirmDeliveredOrders(LocalDateTime executedAt) {
        LocalDateTime deliveredBefore = executedAt.minusDays(properties.thresholdDays());
        List<Delivery> deliveries = deliveryRepository.findAutoConfirmCandidates(
                deliveredBefore,
                DeliveryStatus.DELIVERED,
                OrderStatus.DELIVERED,
                PageRequest.of(0, properties.limit())
        );

        int confirmedCount = 0;
        for (Delivery delivery : deliveries) {
            Order order = delivery.getOrder();
            if (order.getStatus() != OrderStatus.DELIVERED) {
                continue;
            }

            try {
                order.confirm();
                confirmedCount++;
            } catch (IllegalStateException ignored) {
                // A concurrent state change can make a previously eligible order ineligible.
            }
        }

        return new OrderAutoConfirmResult(
                confirmedCount,
                deliveredBefore,
                properties.thresholdDays(),
                executedAt
        );
    }
}
```

- [ ] **Step 6: Run the service test and verify it passes**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.order.OrderAutoConfirmServiceTest
```

Expected: PASS.

- [ ] **Step 7: Commit Task 1**

Run:

```powershell
git add backend/src/main/java/com/sweet/market/MarketApplication.java backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmProperties.java backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmResult.java backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmService.java backend/src/main/java/com/sweet/market/delivery/repository/DeliveryRepository.java backend/src/test/java/com/sweet/market/order/OrderAutoConfirmServiceTest.java
git commit -m "feat: add automatic purchase confirmation service"
```

Expected: commit succeeds. Do not stage `backend/src/main/resources/application.yaml` unless it contains only intentional changes from this task.

---

### Task 2: Admin Trigger API And Scheduler

**Files:**
- Create: `backend/src/test/java/com/sweet/market/order/AdminOrderAutoConfirmApiTest.java`
- Create: `backend/src/main/java/com/sweet/market/order/api/OrderAutoConfirmResponse.java`
- Create: `backend/src/main/java/com/sweet/market/order/api/AdminOrderAutoConfirmController.java`
- Create: `backend/src/main/java/com/sweet/market/order/scheduler/OrderAutoConfirmSchedulingConfig.java`
- Create: `backend/src/main/java/com/sweet/market/order/scheduler/OrderAutoConfirmScheduler.java`

- [ ] **Step 1: Write failing admin API tests**

Create `backend/src/test/java/com/sweet/market/order/AdminOrderAutoConfirmApiTest.java`:

```java
package com.sweet.market.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
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
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.support.IntegrationTestSupport;

import jakarta.persistence.EntityManager;

@TestPropertySource(properties = {
        "spring.batch.job.enabled=false",
        "market.order.auto-confirm.threshold-days=7",
        "market.order.auto-confirm.limit=100"
})
class AdminOrderAutoConfirmApiTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 관리자는_자동_구매확정을_수동_실행한다() throws Exception {
        String adminToken = createAdminAndLogin("admin-auto-confirm@example.com");
        LocalDateTime completedAt = LocalDateTime.now().minusDays(8);
        Long orderId = createDeliveredOrder("api", completedAt);

        mockMvc.perform(post("/api/admin/orders/auto-confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedCount").value(1))
                .andExpect(jsonPath("$.data.thresholdDays").value(7))
                .andExpect(jsonPath("$.data.deliveredBefore").exists())
                .andExpect(jsonPath("$.data.executedAt").exists());

        assertThat(orderRepository.findWithBuyerAndProductById(orderId).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void 일반_회원은_자동_구매확정_수동_실행에_접근할_수_없다() throws Exception {
        String memberToken = signupAndLogin("member-auto-confirm@example.com", "password123", "member");

        mockMvc.perform(post("/api/admin/orders/auto-confirm")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void 인증되지_않은_사용자는_자동_구매확정_수동_실행에_접근할_수_없다() throws Exception {
        mockMvc.perform(post("/api/admin/orders/auto-confirm"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_FAILED"));
    }

    private Long createDeliveredOrder(String suffix, LocalDateTime completedAt) {
        Long deliveryId = transactionTemplate.execute(status -> {
            Member seller = Member.create("seller-admin-auto-" + suffix + "@example.com", "encoded-password", "seller-" + suffix);
            Member buyer = Member.create("buyer-admin-auto-" + suffix + "@example.com", "encoded-password", "buyer-" + suffix);
            entityManager.persist(seller);
            entityManager.persist(buyer);

            Product product = Product.create(seller, "Admin Auto Product " + suffix, "description", 10_000L);
            entityManager.persist(product);

            Order order = Order.create(buyer, product);
            order.markPaid();
            Delivery delivery = Delivery.start(order, "ADMIN-AUTO-" + suffix);
            delivery.complete();
            entityManager.persist(order);
            entityManager.persist(delivery);
            entityManager.flush();
            return delivery.getId();
        });

        jdbcTemplate.update(
                "update deliveries set completed_at = ? where id = ?",
                Timestamp.valueOf(completedAt),
                deliveryId
        );

        return jdbcTemplate.queryForObject(
                "select order_id from deliveries where id = ?",
                Long.class,
                deliveryId
        );
    }

    private String createAdminAndLogin(String email) throws Exception {
        memberRepository.save(Member.createAdmin(
                email,
                passwordEncoder.encode("password123"),
                "admin"
        ));
        return login(email, "password123");
    }

    private String signupAndLogin(String email, String password, String nickname) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new SignupRequest(email, password, nickname))))
                .andExpect(status().isCreated());
        return login(email, password);
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
}
```

- [ ] **Step 2: Run the admin API test and verify it fails**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.order.AdminOrderAutoConfirmApiTest
```

Expected: FAIL with 404 or compilation errors because the controller does not exist.

- [ ] **Step 3: Add the API response and controller**

Create `backend/src/main/java/com/sweet/market/order/api/OrderAutoConfirmResponse.java`:

```java
package com.sweet.market.order.api;

import java.time.LocalDateTime;

import com.sweet.market.order.application.OrderAutoConfirmResult;

public record OrderAutoConfirmResponse(
        int confirmedCount,
        LocalDateTime deliveredBefore,
        int thresholdDays,
        LocalDateTime executedAt
) {

    public static OrderAutoConfirmResponse from(OrderAutoConfirmResult result) {
        return new OrderAutoConfirmResponse(
                result.confirmedCount(),
                result.deliveredBefore(),
                result.thresholdDays(),
                result.executedAt()
        );
    }
}
```

Create `backend/src/main/java/com/sweet/market/order/api/AdminOrderAutoConfirmController.java`:

```java
package com.sweet.market.order.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.order.application.OrderAutoConfirmService;

@RestController
@RequestMapping("/api/admin/orders/auto-confirm")
public class AdminOrderAutoConfirmController {

    private final OrderAutoConfirmService orderAutoConfirmService;

    public AdminOrderAutoConfirmController(OrderAutoConfirmService orderAutoConfirmService) {
        this.orderAutoConfirmService = orderAutoConfirmService;
    }

    @PostMapping
    public ApiResponse<OrderAutoConfirmResponse> run() {
        return ApiResponse.ok(OrderAutoConfirmResponse.from(orderAutoConfirmService.confirmDeliveredOrders()));
    }
}
```

- [ ] **Step 4: Add local/dev scheduling**

Create `backend/src/main/java/com/sweet/market/order/scheduler/OrderAutoConfirmSchedulingConfig.java`:

```java
package com.sweet.market.order.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@Profile({"local", "dev"})
public class OrderAutoConfirmSchedulingConfig {
}
```

Create `backend/src/main/java/com/sweet/market/order/scheduler/OrderAutoConfirmScheduler.java`:

```java
package com.sweet.market.order.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.sweet.market.order.application.OrderAutoConfirmResult;
import com.sweet.market.order.application.OrderAutoConfirmService;

@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "market.order.auto-confirm", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderAutoConfirmScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoConfirmScheduler.class);

    private final OrderAutoConfirmService orderAutoConfirmService;

    public OrderAutoConfirmScheduler(OrderAutoConfirmService orderAutoConfirmService) {
        this.orderAutoConfirmService = orderAutoConfirmService;
    }

    @Scheduled(fixedDelayString = "${market.order.auto-confirm.fixed-delay:PT1H}")
    public void confirmDeliveredOrders() {
        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders();
        log.info(
                "Automatic purchase confirmation completed. confirmedCount={}, deliveredBefore={}, thresholdDays={}, executedAt={}",
                result.confirmedCount(),
                result.deliveredBefore(),
                result.thresholdDays(),
                result.executedAt()
        );
    }
}
```

- [ ] **Step 5: Run the admin API test and verify it passes**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.order.AdminOrderAutoConfirmApiTest
```

Expected: PASS.

- [ ] **Step 6: Commit Task 2**

Run:

```powershell
git add backend/src/main/java/com/sweet/market/order/api/OrderAutoConfirmResponse.java backend/src/main/java/com/sweet/market/order/api/AdminOrderAutoConfirmController.java backend/src/main/java/com/sweet/market/order/scheduler/OrderAutoConfirmSchedulingConfig.java backend/src/main/java/com/sweet/market/order/scheduler/OrderAutoConfirmScheduler.java backend/src/test/java/com/sweet/market/order/AdminOrderAutoConfirmApiTest.java
git commit -m "feat: add admin auto confirmation trigger"
```

Expected: commit succeeds.

---

### Task 3: Web Admin Manual Trigger Panel

**Files:**
- Modify: `web/src/features/admin/adminBatchApi.ts`
- Modify: `web/src/pages/AdminSettlementBatchPage.tsx`

- [ ] **Step 1: Add the admin API client**

Modify `web/src/features/admin/adminBatchApi.ts` by appending these types and function:

```ts
export type OrderAutoConfirmResult = {
  confirmedCount: number;
  deliveredBefore: string;
  thresholdDays: number;
  executedAt: string;
};

export function runOrderAutoConfirm() {
  return api<OrderAutoConfirmResult>('/api/admin/orders/auto-confirm', {
    method: 'POST',
  });
}
```

- [ ] **Step 2: Add the panel to the admin page**

Modify imports in `web/src/pages/AdminSettlementBatchPage.tsx`:

```ts
import {
  getSettlementBatchExecution,
  getSettlementBatchExecutions,
  runOrderAutoConfirm,
  runSettlementBatch,
  type OrderAutoConfirmResult,
  type RunSettlementBatchInput,
  type SettlementBatchRunResult,
} from '../features/admin/adminBatchApi';
```

Add state and mutation near the existing `lastRunResult` state:

```ts
const [lastAutoConfirmResult, setLastAutoConfirmResult] = useState<OrderAutoConfirmResult | null>(null);
const [autoConfirmError, setAutoConfirmError] = useState<string | null>(null);
```

Add this mutation after the existing `runMutation`:

```ts
const autoConfirmMutation = useMutation({
  mutationFn: runOrderAutoConfirm,
  onSuccess: async (result) => {
    setLastAutoConfirmResult(result);
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['my-orders'] }),
      queryClient.invalidateQueries({ queryKey: ['products'] }),
    ]);
  },
});
```

Add this handler before `const onSubmit = ...`:

```ts
async function runAutoConfirm() {
  setAutoConfirmError(null);

  try {
    await autoConfirmMutation.mutateAsync();
  } catch (caughtError) {
    setAutoConfirmError(toErrorMessage(caughtError));
  }
}
```

Add this section just before the existing `<div className="admin-batch-layout">`:

```tsx
<section className="admin-tool-panel" aria-labelledby="auto-confirm-title">
  <h2 id="auto-confirm-title">자동 구매 확정</h2>
  <p className="status-text">배송 완료 후 7일이 지난 거래를 구매 확정 상태로 전환합니다.</p>
  {autoConfirmError ? <p className="error-text">{autoConfirmError}</p> : null}
  <button type="button" className="text-button" disabled={autoConfirmMutation.isPending} onClick={runAutoConfirm}>
    {autoConfirmMutation.isPending ? '실행 중' : '자동 구매 확정 실행'}
  </button>

  {lastAutoConfirmResult ? (
    <div className="admin-result-panel" aria-live="polite">
      <h3>최근 자동 확정 결과</h3>
      <dl className="compact-definition-list">
        <div>
          <dt>확정 건수</dt>
          <dd>{lastAutoConfirmResult.confirmedCount}</dd>
        </div>
        <div>
          <dt>기준 기간</dt>
          <dd>{lastAutoConfirmResult.thresholdDays}일</dd>
        </div>
        <div>
          <dt>배송 완료 기준</dt>
          <dd>{formatParameterDate(lastAutoConfirmResult.deliveredBefore)}</dd>
        </div>
        <div>
          <dt>실행 일시</dt>
          <dd>{formatParameterDate(lastAutoConfirmResult.executedAt)}</dd>
        </div>
      </dl>
    </div>
  ) : null}
</section>
```

- [ ] **Step 3: Build the web app and fix type errors**

Run:

```powershell
cd web
npm run build
```

Expected: PASS. If TypeScript complains about function placement, keep `runAutoConfirm` inside `AdminSettlementBatchPage` before the JSX return and keep `toErrorMessage` as the shared helper at the bottom of the file.

- [ ] **Step 4: Commit Task 3**

Run:

```powershell
git add web/src/features/admin/adminBatchApi.ts web/src/pages/AdminSettlementBatchPage.tsx
git commit -m "feat: add admin auto confirmation panel"
```

Expected: commit succeeds.

---

### Task 4: Full Verification

**Files:**
- No code files unless verification exposes a bug.

- [ ] **Step 1: Run backend tests**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
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

- [ ] **Step 3: Run git checks**

Run:

```powershell
git diff --check
git status --short --branch --untracked-files=all
```

Expected: `git diff --check` has no output. `git status` may still show the pre-existing local `backend/src/main/resources/application.yaml` modification, but it should not show unstaged files from this milestone.

- [ ] **Step 4: Final commit if verification required fixes**

If Task 4 required any fix commits, run:

```powershell
git add <fixed-files>
git commit -m "fix: stabilize automatic purchase confirmation"
```

Expected: commit succeeds. Do not stage unrelated local files.

---

## Self-Review

- Spec coverage: service, 7-day threshold, idempotency, local/dev scheduler, admin trigger, web button, and existing settlement-batch boundary are all covered.
- Placeholder scan: no `TBD`, `TODO`, or unspecified implementation steps remain.
- Type consistency: `OrderAutoConfirmResult`, `OrderAutoConfirmResponse`, `runOrderAutoConfirm`, and `/api/admin/orders/auto-confirm` are used consistently across tasks.
- Scope check: implementation stays inside order/delivery/admin web surfaces and does not add batch infrastructure or settlement creation.
