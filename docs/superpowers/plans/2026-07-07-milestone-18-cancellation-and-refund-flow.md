# Milestone 18 Cancellation And Refund Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build pre-delivery cancellation and post-delivery refund request handling with seller/admin approval, settlement blocking, and buyer web entry points.

**Architecture:** Keep immediate cancellation on the existing order/payment flow, and add a focused `refund` package for delivered-order refund requests. `Order` owns the coarse transaction state, `Payment` owns payment/refund state, and `RefundRequest` owns request reason, handler, and approval/rejection history.

**Tech Stack:** Spring Boot 3, Spring Data JPA, Spring Security, JUnit 5, MockMvc, PostgreSQL/Testcontainers, React, TypeScript, TanStack Query, Vite.

---

## Spec And Safety Notes

Read before implementation:

- `docs/superpowers/specs/2026-07-07-milestone-18-cancellation-and-refund-flow-design.md`
- `AGENTS.md`

Do not stage, overwrite, reset, or discard `backend/src/main/resources/application.yaml`. It has an existing local-only development change in the main checkout.

Use JDK 21 for backend commands:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
```

All new JUnit `@Test` method names must be Korean with underscores.

## File Structure

Backend files to create:

- `backend/src/main/java/com/sweet/market/refund/domain/RefundRequestStatus.java`: enum for `REQUESTED`, `APPROVED`, `REJECTED`.
- `backend/src/main/java/com/sweet/market/refund/domain/RefundRequest.java`: entity and domain transitions.
- `backend/src/main/java/com/sweet/market/refund/repository/RefundRequestRepository.java`: query methods for duplicate checks and handler fetches.
- `backend/src/main/java/com/sweet/market/refund/api/RefundRequestCreateRequest.java`: buyer request DTO.
- `backend/src/main/java/com/sweet/market/refund/api/RefundRejectRequest.java`: seller/admin rejection DTO.
- `backend/src/main/java/com/sweet/market/refund/api/RefundRequestResponse.java`: response DTO.
- `backend/src/main/java/com/sweet/market/refund/api/RefundRequestController.java`: buyer refund request endpoint.
- `backend/src/main/java/com/sweet/market/refund/api/SellerRefundRequestController.java`: seller approve/reject endpoints.
- `backend/src/main/java/com/sweet/market/refund/api/AdminRefundRequestController.java`: admin approve/reject endpoints.
- `backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java`: transaction service for create/approve/reject.
- `backend/src/test/java/com/sweet/market/refund/domain/RefundRequestTest.java`: domain transition tests.
- `backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java`: buyer/seller/admin API tests.

Backend files to modify:

- `backend/src/main/java/com/sweet/market/order/domain/OrderStatus.java`: add `REFUND_REQUESTED`, `REFUNDED`.
- `backend/src/main/java/com/sweet/market/order/domain/Order.java`: add refund state transitions and idempotent cancel handling.
- `backend/src/main/java/com/sweet/market/payment/domain/PaymentStatus.java`: add `REFUNDED`.
- `backend/src/main/java/com/sweet/market/payment/domain/Payment.java`: add idempotent cancellation and refund transition.
- `backend/src/main/java/com/sweet/market/order/application/OrderService.java`: route `PAID` order cancellation through payment.
- `backend/src/main/java/com/sweet/market/order/api/OrderResponse.java`: include refund summary fields.
- `backend/src/main/java/com/sweet/market/order/api/OrderSummaryResponse.java`: include refund summary fields.
- `backend/src/main/java/com/sweet/market/order/query/OrderQueryService.java`: attach refund summaries to buyer reads.
- `backend/src/main/java/com/sweet/market/order/repository/OrderRepository.java`: add fetch method for cancellation if needed.
- `backend/src/main/java/com/sweet/market/payment/api/PaymentResponse.java`: include `REFUNDED` naturally through status enum.
- `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`: add refund errors.
- `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`: truncate `refund_requests`.
- `backend/src/test/java/com/sweet/market/order/OrderApiTest.java`: update idempotent cancel expectation and paid cancellation tests.
- `backend/src/test/java/com/sweet/market/order/OrderConfirmApiTest.java`: add refund-pending confirmation block.
- `backend/src/test/java/com/sweet/market/settlement/SettlementApiTest.java`: add refund-pending/refunded settlement rejection tests.
- `backend/src/test/java/com/sweet/market/settlement/batch/SettlementBatchJobTest.java`: add refund-pending/refunded batch exclusion tests.

Web files to modify:

- `web/src/features/orders/orderApi.ts`: add refund statuses, request type, and `createRefundRequest()`.
- `web/src/pages/MyOrdersPage.tsx`: add refund form, status badges, and pending-state action rules.
- `web/src/shared/styles.css`: add compact refund form/status styles if existing classes are insufficient.

## Task 1: Domain State Transitions

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/order/domain/OrderStatus.java`
- Modify: `backend/src/main/java/com/sweet/market/order/domain/Order.java`
- Modify: `backend/src/main/java/com/sweet/market/payment/domain/PaymentStatus.java`
- Modify: `backend/src/main/java/com/sweet/market/payment/domain/Payment.java`
- Create: `backend/src/main/java/com/sweet/market/refund/domain/RefundRequestStatus.java`
- Create: `backend/src/main/java/com/sweet/market/refund/domain/RefundRequest.java`
- Test: `backend/src/test/java/com/sweet/market/refund/domain/RefundRequestTest.java`
- Test: `backend/src/test/java/com/sweet/market/order/domain/OrderTest.java`
- Test: `backend/src/test/java/com/sweet/market/payment/domain/PaymentTest.java`

- [ ] **Step 1: Write failing refund domain tests**

Add `backend/src/test/java/com/sweet/market/refund/domain/RefundRequestTest.java`:

```java
package com.sweet.market.refund.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.product.domain.Product;

class RefundRequestTest {

    @Test
    void 배송완료_주문은_환불_요청할_수_있다() {
        Order order = deliveredOrder();

        RefundRequest refundRequest = RefundRequest.request(order, order.getBuyer(), "상품 상태가 설명과 달라 환불을 요청합니다.");

        assertThat(refundRequest.getOrder()).isSameAs(order);
        assertThat(refundRequest.getBuyer()).isSameAs(order.getBuyer());
        assertThat(refundRequest.getReason()).isEqualTo("상품 상태가 설명과 달라 환불을 요청합니다.");
        assertThat(refundRequest.getStatus()).isEqualTo(RefundRequestStatus.REQUESTED);
        assertThat(refundRequest.getRequestedAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUND_REQUESTED);
    }

    @Test
    void 요청된_환불을_승인하면_주문이_환불완료가_된다() {
        Order order = deliveredOrder();
        Member handler = member("handler@example.com", "handler");
        RefundRequest refundRequest = RefundRequest.request(order, order.getBuyer(), "상품 상태가 설명과 달라 환불을 요청합니다.");

        refundRequest.approve(handler);

        assertThat(refundRequest.getStatus()).isEqualTo(RefundRequestStatus.APPROVED);
        assertThat(refundRequest.getHandledBy()).isSameAs(handler);
        assertThat(refundRequest.getHandledAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void 요청된_환불을_거절하면_주문이_배송완료로_돌아간다() {
        Order order = deliveredOrder();
        Member handler = member("handler@example.com", "handler");
        RefundRequest refundRequest = RefundRequest.request(order, order.getBuyer(), "상품 상태가 설명과 달라 환불을 요청합니다.");

        refundRequest.reject(handler, "상품 설명과 다른 부분을 확인할 수 없습니다.");

        assertThat(refundRequest.getStatus()).isEqualTo(RefundRequestStatus.REJECTED);
        assertThat(refundRequest.getRejectReason()).isEqualTo("상품 설명과 다른 부분을 확인할 수 없습니다.");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void 요청_상태가_아닌_환불은_다시_처리할_수_없다() {
        Order order = deliveredOrder();
        Member handler = member("handler@example.com", "handler");
        RefundRequest refundRequest = RefundRequest.request(order, order.getBuyer(), "상품 상태가 설명과 달라 환불을 요청합니다.");
        refundRequest.approve(handler);

        assertThatThrownBy(() -> refundRequest.reject(handler, "거절 사유입니다."))
                .isInstanceOf(IllegalStateException.class);
    }

    private Order deliveredOrder() {
        Member seller = member("seller@example.com", "seller");
        Member buyer = member("buyer@example.com", "buyer");
        Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000);
        Order order = Order.create(buyer, product);
        order.markPaid();
        order.startShipping();
        order.completeDelivery();
        return order;
    }

    private Member member(String email, String nickname) {
        return Member.create(email, "encoded-password", nickname);
    }
}
```

- [ ] **Step 2: Extend existing order/payment domain tests**

Add these tests to `backend/src/test/java/com/sweet/market/order/domain/OrderTest.java`:

```java
@Test
void 환불_요청중인_주문은_구매확정할_수_없다() {
    Order order = deliveredOrder();
    order.requestRefund();

    assertThatThrownBy(order::confirm)
            .isInstanceOf(IllegalStateException.class);
}

@Test
void 취소된_주문은_다시_취소해도_상태가_유지된다() {
    Order order = createdOrder();
    order.cancel();

    order.cancel();

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
    assertThat(order.getProduct().getStatus()).isEqualTo(ProductStatus.ON_SALE);
}
```

Add these tests to `backend/src/test/java/com/sweet/market/payment/domain/PaymentTest.java`:

```java
@Test
void 승인된_결제는_환불완료로_변경할_수_있다() {
    Order order = createdOrder();
    Payment payment = Payment.approve(order, "fake-payment-1");

    payment.refund();

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
}

@Test
void 취소된_결제는_다시_취소해도_상태가_유지된다() {
    Order order = createdOrder();
    Payment payment = Payment.approve(order, "fake-payment-1");
    payment.cancel();

    payment.cancel();

    assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELED);
}
```

- [ ] **Step 3: Run domain tests and verify failure**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "*RefundRequestTest" --tests "*OrderTest" --tests "*PaymentTest"
```

Expected: FAIL because `RefundRequest`, `RefundRequestStatus`, `OrderStatus.REFUND_REQUESTED`, `OrderStatus.REFUNDED`, `PaymentStatus.REFUNDED`, `Order.requestRefund()`, `Order.markRefunded()`, and `Payment.refund()` do not exist.

- [ ] **Step 4: Implement order and payment states**

Change `OrderStatus` to:

```java
package com.sweet.market.order.domain;

public enum OrderStatus {
    CREATED,
    PAID,
    SHIPPING,
    DELIVERED,
    REFUND_REQUESTED,
    REFUNDED,
    CONFIRMED,
    CANCELED
}
```

Change `PaymentStatus` to:

```java
package com.sweet.market.payment.domain;

public enum PaymentStatus {
    READY,
    APPROVED,
    CANCELED,
    REFUNDED,
    FAILED
}
```

Add these methods to `Order` and adjust `cancel()` for idempotency:

```java
public void cancel() {
    if (status == OrderStatus.CANCELED) {
        return;
    }
    if (status != OrderStatus.CREATED) {
        throw new IllegalStateException("Order cannot be canceled: " + status);
    }
    product.restoreOnSaleFromReservation();
    this.status = OrderStatus.CANCELED;
    this.canceledAt = LocalDateTime.now();
}

public void cancelPaidOrder() {
    if (status == OrderStatus.CANCELED) {
        return;
    }
    if (status != OrderStatus.PAID) {
        throw new IllegalStateException("Paid order cannot be canceled: " + status);
    }
    product.restoreOnSaleFromReservation();
    this.status = OrderStatus.CANCELED;
    this.canceledAt = LocalDateTime.now();
}

public void requestRefund() {
    if (status != OrderStatus.DELIVERED) {
        throw new IllegalStateException("Order cannot request refund: " + status);
    }
    this.status = OrderStatus.REFUND_REQUESTED;
}

public void markRefunded() {
    if (status != OrderStatus.REFUND_REQUESTED) {
        throw new IllegalStateException("Order cannot be refunded: " + status);
    }
    this.status = OrderStatus.REFUNDED;
}

public void rejectRefund() {
    if (status != OrderStatus.REFUND_REQUESTED) {
        throw new IllegalStateException("Order refund cannot be rejected: " + status);
    }
    this.status = OrderStatus.DELIVERED;
}
```

Add these methods to `Payment` and adjust `cancel()` for idempotency:

```java
public void cancel() {
    if (status == PaymentStatus.CANCELED) {
        return;
    }
    if (status != PaymentStatus.APPROVED) {
        throw new IllegalStateException("Payment cannot be canceled: " + status);
    }
    order.cancelPaidOrder();
    this.status = PaymentStatus.CANCELED;
    this.canceledAt = LocalDateTime.now();
}

public void refund() {
    if (status != PaymentStatus.APPROVED) {
        throw new IllegalStateException("Payment cannot be refunded: " + status);
    }
    this.status = PaymentStatus.REFUNDED;
    this.canceledAt = LocalDateTime.now();
}
```

- [ ] **Step 5: Implement refund domain**

Create `RefundRequestStatus`:

```java
package com.sweet.market.refund.domain;

public enum RefundRequestStatus {
    REQUESTED,
    APPROVED,
    REJECTED
}
```

Create `RefundRequest`:

```java
package com.sweet.market.refund.domain;

import java.time.LocalDateTime;

import com.sweet.market.member.domain.Member;
import com.sweet.market.order.domain.Order;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "refund_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_refund_requests_order", columnNames = "order_id"),
        indexes = @Index(name = "idx_refund_requests_status_requested_at_id", columnList = "status, requested_at, id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_id", nullable = false)
    private Member buyer;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundRequestStatus status;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_id")
    private Member handledBy;

    private LocalDateTime handledAt;

    @Column(length = 500)
    private String rejectReason;

    private RefundRequest(Order order, Member buyer, String reason) {
        this.order = order;
        this.buyer = buyer;
        this.reason = reason;
        this.status = RefundRequestStatus.REQUESTED;
        this.requestedAt = LocalDateTime.now();
    }

    public static RefundRequest request(Order order, Member buyer, String reason) {
        order.requestRefund();
        return new RefundRequest(order, buyer, reason);
    }

    public void approve(Member handler) {
        validateRequested();
        this.status = RefundRequestStatus.APPROVED;
        this.handledBy = handler;
        this.handledAt = LocalDateTime.now();
        this.order.markRefunded();
    }

    public void reject(Member handler, String rejectReason) {
        validateRequested();
        this.status = RefundRequestStatus.REJECTED;
        this.handledBy = handler;
        this.handledAt = LocalDateTime.now();
        this.rejectReason = rejectReason;
        this.order.rejectRefund();
    }

    public boolean isSellerOwnedBy(Long sellerId) {
        return order.getProduct().isOwnedBy(sellerId);
    }

    private void validateRequested() {
        if (status != RefundRequestStatus.REQUESTED) {
            throw new IllegalStateException("Refund request cannot be handled: " + status);
        }
    }
}
```

- [ ] **Step 6: Run domain tests and commit**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*RefundRequestTest" --tests "*OrderTest" --tests "*PaymentTest"
```

Expected: PASS.

Commit:

```powershell
git add backend/src/main/java/com/sweet/market/order/domain/OrderStatus.java backend/src/main/java/com/sweet/market/order/domain/Order.java backend/src/main/java/com/sweet/market/payment/domain/PaymentStatus.java backend/src/main/java/com/sweet/market/payment/domain/Payment.java backend/src/main/java/com/sweet/market/refund/domain/RefundRequestStatus.java backend/src/main/java/com/sweet/market/refund/domain/RefundRequest.java backend/src/test/java/com/sweet/market/refund/domain/RefundRequestTest.java backend/src/test/java/com/sweet/market/order/domain/OrderTest.java backend/src/test/java/com/sweet/market/payment/domain/PaymentTest.java
git commit -m "feat: add refund domain states"
```

## Task 2: Buyer Refund Request API

**Files:**

- Create: `backend/src/main/java/com/sweet/market/refund/repository/RefundRequestRepository.java`
- Create: `backend/src/main/java/com/sweet/market/refund/api/RefundRequestCreateRequest.java`
- Create: `backend/src/main/java/com/sweet/market/refund/api/RefundRequestResponse.java`
- Create: `backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java`
- Create: `backend/src/main/java/com/sweet/market/refund/api/RefundRequestController.java`
- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`
- Test: `backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java`

- [ ] **Step 1: Write failing buyer API tests**

Create `RefundRequestApiTest` with buyer scenarios:

```java
package com.sweet.market.refund;

import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.sweet.market.auth.api.LoginRequest;
import com.sweet.market.auth.api.SignupRequest;
import com.sweet.market.support.IntegrationTestSupport;

class RefundRequestApiTest extends IntegrationTestSupport {

    @Test
    void 구매자는_배송완료_주문에_환불을_요청할_수_있다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.productId").value(productId))
                .andExpect(jsonPath("$.data.productTitle").value("MacBook Pro"))
                .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                .andExpect(jsonPath("$.data.reason").value("상품 상태가 설명과 달라 환불을 요청합니다."))
                .andExpect(jsonPath("$.data.requestedAt").exists());
    }

    @Test
    void 구매자는_다른_사람의_주문에_환불을_요청할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        String otherToken = signupAndLogin("other@example.com", "password123", "other");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ACCESS_DENIED"));
    }

    @Test
    void 배송완료가_아닌_주문은_환불을_요청할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createOrder(buyerToken, productId);

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFUND_REQUEST_NOT_ALLOWED"));
    }

    @Test
    void 같은_주문에는_환불을_중복_요청할_수_없다() throws Exception {
        String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
        String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
        Long productId = createProduct(sellerToken, "MacBook Pro");
        Long orderId = createDeliveredOrder(buyerToken, productId);
        createRefundRequest(buyerToken, orderId);

        mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_REFUND_REQUEST"));
    }

    private Long createRefundRequest(String accessToken, Long orderId) throws Exception {
        String response = mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("id").asLong();
    }

    private String signupAndLogin(String email, String password, String nickname) throws Exception {
        SignupRequest signupRequest = new SignupRequest(email, password, nickname);
        LoginRequest loginRequest = new LoginRequest(email, password);
        mockMvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(json(signupRequest)))
                .andExpect(status().isCreated());
        String response = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken", not(blankOrNullString())))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private Long createProduct(String accessToken, String title) throws Exception {
        Long uploadId = uploadImage(accessToken, title.replace(" ", "-").toLowerCase() + ".jpg");
        String response = mockMvc.perform(post("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "%s",
                                  "description": "M3 laptop",
                                  "price": 2000000,
                                  "images": [{"uploadId": %d, "sortOrder": 0, "representative": true}]
                                }
                                """.formatted(title, uploadId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long uploadImage(String accessToken, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, MediaType.IMAGE_JPEG_VALUE, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});
        String response = mockMvc.perform(multipart("/api/product-image-uploads").file(file).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long createOrder(String accessToken, Long productId) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "productId": %d
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private Long createDeliveredOrder(String accessToken, Long productId) throws Exception {
        Long orderId = createOrder(accessToken, productId);
        mockMvc.perform(post("/api/payments/{orderId}/approve", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/{orderId}/start", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).andExpect(status().isOk());
        mockMvc.perform(post("/api/deliveries/{orderId}/complete", orderId).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)).andExpect(status().isOk());
        return orderId;
    }
}
```

- [ ] **Step 2: Run buyer API tests and verify failure**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*RefundRequestApiTest"
```

Expected: FAIL because refund API classes, repository, service, endpoint, errors, and cleanup table registration do not exist.

- [ ] **Step 3: Add refund error codes and cleanup table**

Add to `ErrorCode` before payment errors:

```java
REFUND_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "환불 요청을 찾을 수 없습니다."),
REFUND_REQUEST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "환불 요청에 대한 권한이 없습니다."),
REFUND_REQUEST_NOT_ALLOWED(HttpStatus.CONFLICT, "환불 요청할 수 없는 주문 상태입니다."),
DUPLICATE_REFUND_REQUEST(HttpStatus.CONFLICT, "이미 환불 요청된 주문입니다."),
REFUND_REQUEST_HANDLE_NOT_ALLOWED(HttpStatus.CONFLICT, "처리할 수 없는 환불 요청 상태입니다."),
```

Change `IntegrationTestSupport.cleanUp()` to truncate `refund_requests` before `payments` and `orders`:

```java
jdbcTemplate.execute("TRUNCATE TABLE settlements, deliveries, refund_requests, payments, reviews, orders, cart_items, wishlist_items, product_image_uploads, product_images, products, members RESTART IDENTITY CASCADE");
```

- [ ] **Step 4: Implement repository and DTOs**

Create `RefundRequestRepository`:

```java
package com.sweet.market.refund.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.sweet.market.refund.domain.RefundRequest;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    boolean existsByOrderId(Long orderId);

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "buyer", "handledBy"})
    Optional<RefundRequest> findWithOrderById(Long id);

    @EntityGraph(attributePaths = {"order", "order.buyer", "order.product", "order.product.seller", "buyer", "handledBy"})
    Optional<RefundRequest> findWithOrderByOrderId(Long orderId);
}
```

Create `RefundRequestCreateRequest`:

```java
package com.sweet.market.refund.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRequestCreateRequest(
        @NotBlank
        @Size(min = 10, max = 500)
        String reason
) {
}
```

Create `RefundRejectRequest`:

```java
package com.sweet.market.refund.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRejectRequest(
        @NotBlank
        @Size(min = 5, max = 500)
        String rejectReason
) {
}
```

Create `RefundRequestResponse`:

```java
package com.sweet.market.refund.api;

import java.time.LocalDateTime;

import com.sweet.market.refund.domain.RefundRequest;

public record RefundRequestResponse(
        Long id,
        Long orderId,
        Long productId,
        String productTitle,
        Long buyerId,
        String reason,
        String status,
        LocalDateTime requestedAt,
        Long handledById,
        LocalDateTime handledAt,
        String rejectReason
) {

    public static RefundRequestResponse from(RefundRequest refundRequest) {
        return new RefundRequestResponse(
                refundRequest.getId(),
                refundRequest.getOrder().getId(),
                refundRequest.getOrder().getProduct().getId(),
                refundRequest.getOrder().getProduct().getTitle(),
                refundRequest.getBuyer().getId(),
                refundRequest.getReason(),
                refundRequest.getStatus().name(),
                refundRequest.getRequestedAt(),
                refundRequest.getHandledBy() == null ? null : refundRequest.getHandledBy().getId(),
                refundRequest.getHandledAt(),
                refundRequest.getRejectReason()
        );
    }
}
```

- [ ] **Step 5: Implement buyer service and controller**

Create `RefundRequestService` with buyer creation:

```java
package com.sweet.market.refund.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.common.error.BusinessException;
import com.sweet.market.common.error.ErrorCode;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.refund.api.RefundRequestResponse;
import com.sweet.market.refund.domain.RefundRequest;
import com.sweet.market.refund.repository.RefundRequestRepository;

@Service
public class RefundRequestService {

    private final RefundRequestRepository refundRequestRepository;
    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;

    public RefundRequestService(
            RefundRequestRepository refundRequestRepository,
            OrderRepository orderRepository,
            MemberRepository memberRepository
    ) {
        this.refundRequestRepository = refundRequestRepository;
        this.orderRepository = orderRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public RefundRequestResponse create(Long buyerId, Long orderId, String reason) {
        Order order = orderRepository.findWithBuyerAndProductById(orderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
        if (!order.isOwnedBy(buyerId)) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_ACCESS_DENIED);
        }
        if (refundRequestRepository.existsByOrderId(orderId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }
        Member buyer = memberRepository.findById(buyerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        try {
            RefundRequest refundRequest = RefundRequest.request(order, buyer, reason);
            RefundRequest savedRefundRequest = refundRequestRepository.saveAndFlush(refundRequest);
            return RefundRequestResponse.from(savedRefundRequest);
        } catch (IllegalStateException exception) {
            throw new BusinessException(ErrorCode.REFUND_REQUEST_NOT_ALLOWED);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.DUPLICATE_REFUND_REQUEST);
        }
    }
}
```

Create `RefundRequestController`:

```java
package com.sweet.market.refund.api;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.refund.application.RefundRequestService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/orders/{orderId}/refund-requests")
public class RefundRequestController {

    private final RefundRequestService refundRequestService;

    public RefundRequestController(RefundRequestService refundRequestService) {
        this.refundRequestService = refundRequestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<RefundRequestResponse> create(
            Authentication authentication,
            @PathVariable Long orderId,
            @Valid @RequestBody RefundRequestCreateRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.create(member.id(), orderId, request.reason()));
    }
}
```

- [ ] **Step 6: Run buyer API tests and commit**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*RefundRequestApiTest"
```

Expected: PASS for the buyer tests added in this task.

Commit:

```powershell
git add backend/src/main/java/com/sweet/market/common/error/ErrorCode.java backend/src/main/java/com/sweet/market/refund backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java
git commit -m "feat: add buyer refund requests"
```

## Task 3: Seller And Admin Refund Handling

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java`
- Create: `backend/src/main/java/com/sweet/market/refund/api/SellerRefundRequestController.java`
- Create: `backend/src/main/java/com/sweet/market/refund/api/AdminRefundRequestController.java`
- Modify: `backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java`

- [ ] **Step 1: Write failing seller/admin API tests**

Add these tests to `RefundRequestApiTest`:

```java
@Test
void 판매자는_자신의_상품_환불_요청을_승인할_수_있다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "MacBook Pro");
    Long orderId = createDeliveredOrder(buyerToken, productId);
    Long refundRequestId = createRefundRequest(buyerToken, orderId);

    mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/approve", refundRequestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"))
            .andExpect(jsonPath("$.data.handledById").isNumber())
            .andExpect(jsonPath("$.data.handledAt").exists());
}

@Test
void 판매자는_자신의_상품_환불_요청을_거절할_수_있다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "MacBook Pro");
    Long orderId = createDeliveredOrder(buyerToken, productId);
    Long refundRequestId = createRefundRequest(buyerToken, orderId);

    mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/reject", refundRequestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "rejectReason": "상품 설명과 다른 부분을 확인할 수 없습니다."
                            }
                            """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REJECTED"))
            .andExpect(jsonPath("$.data.rejectReason").value("상품 설명과 다른 부분을 확인할 수 없습니다."));
}

@Test
void 판매자는_다른_판매자의_환불_요청을_처리할_수_없다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String otherSellerToken = signupAndLogin("other-seller@example.com", "password123", "otherSeller");
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "MacBook Pro");
    Long orderId = createDeliveredOrder(buyerToken, productId);
    Long refundRequestId = createRefundRequest(buyerToken, orderId);

    mockMvc.perform(post("/api/seller/refund-requests/{refundRequestId}/approve", refundRequestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherSellerToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("REFUND_REQUEST_ACCESS_DENIED"));
}

@Test
void 관리자는_모든_환불_요청을_승인할_수_있다() throws Exception {
    String adminToken = signupAndLogin("admin@example.com", "password123", "admin");
    jdbcTemplate.update("update members set role = 'ADMIN' where email = ?", "admin@example.com");
    adminToken = login("admin@example.com", "password123");
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "MacBook Pro");
    Long orderId = createDeliveredOrder(buyerToken, productId);
    Long refundRequestId = createRefundRequest(buyerToken, orderId);

    mockMvc.perform(post("/api/admin/refund-requests/{refundRequestId}/approve", refundRequestId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("APPROVED"));
}
```

If the test class does not have `login(String email, String password)`, add:

```java
private String login(String email, String password) throws Exception {
    LoginRequest loginRequest = new LoginRequest(email, password);
    String response = mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json(loginRequest)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).path("data").path("accessToken").asText();
}
```

- [ ] **Step 2: Run seller/admin tests and verify failure**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*RefundRequestApiTest"
```

Expected: FAIL because seller/admin controllers and service methods do not exist.

- [ ] **Step 3: Add approve/reject service methods**

Add these imports to `RefundRequestService`:

```java
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.repository.PaymentRepository;
```

Add `PaymentRepository` constructor dependency.

Add service methods:

```java
@Transactional
public RefundRequestResponse approveBySeller(Long sellerId, Long refundRequestId) {
    RefundRequest refundRequest = refundRequestRepository.findWithOrderById(refundRequestId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
    if (!refundRequest.isSellerOwnedBy(sellerId)) {
        throw new BusinessException(ErrorCode.REFUND_REQUEST_ACCESS_DENIED);
    }
    return approve(refundRequest, sellerId);
}

@Transactional
public RefundRequestResponse rejectBySeller(Long sellerId, Long refundRequestId, String rejectReason) {
    RefundRequest refundRequest = refundRequestRepository.findWithOrderById(refundRequestId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
    if (!refundRequest.isSellerOwnedBy(sellerId)) {
        throw new BusinessException(ErrorCode.REFUND_REQUEST_ACCESS_DENIED);
    }
    return reject(refundRequest, sellerId, rejectReason);
}

@Transactional
public RefundRequestResponse approveByAdmin(Long adminId, Long refundRequestId) {
    RefundRequest refundRequest = refundRequestRepository.findWithOrderById(refundRequestId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
    return approve(refundRequest, adminId);
}

@Transactional
public RefundRequestResponse rejectByAdmin(Long adminId, Long refundRequestId, String rejectReason) {
    RefundRequest refundRequest = refundRequestRepository.findWithOrderById(refundRequestId)
            .orElseThrow(() -> new BusinessException(ErrorCode.REFUND_REQUEST_NOT_FOUND));
    return reject(refundRequest, adminId, rejectReason);
}

private RefundRequestResponse approve(RefundRequest refundRequest, Long handlerId) {
    Member handler = memberRepository.findById(handlerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    Payment payment = paymentRepository.findWithOrderByOrderId(refundRequest.getOrder().getId())
            .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    try {
        refundRequest.approve(handler);
        payment.refund();
        return RefundRequestResponse.from(refundRequest);
    } catch (IllegalStateException exception) {
        throw new BusinessException(ErrorCode.REFUND_REQUEST_HANDLE_NOT_ALLOWED);
    }
}

private RefundRequestResponse reject(RefundRequest refundRequest, Long handlerId, String rejectReason) {
    Member handler = memberRepository.findById(handlerId)
            .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    try {
        refundRequest.reject(handler, rejectReason);
        return RefundRequestResponse.from(refundRequest);
    } catch (IllegalStateException exception) {
        throw new BusinessException(ErrorCode.REFUND_REQUEST_HANDLE_NOT_ALLOWED);
    }
}
```

- [ ] **Step 4: Add seller/admin controllers**

Create `SellerRefundRequestController`:

```java
package com.sweet.market.refund.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.refund.application.RefundRequestService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/seller/refund-requests")
public class SellerRefundRequestController {

    private final RefundRequestService refundRequestService;

    public SellerRefundRequestController(RefundRequestService refundRequestService) {
        this.refundRequestService = refundRequestService;
    }

    @PostMapping("/{refundRequestId}/approve")
    public ApiResponse<RefundRequestResponse> approve(Authentication authentication, @PathVariable Long refundRequestId) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.approveBySeller(member.id(), refundRequestId));
    }

    @PostMapping("/{refundRequestId}/reject")
    public ApiResponse<RefundRequestResponse> reject(
            Authentication authentication,
            @PathVariable Long refundRequestId,
            @Valid @RequestBody RefundRejectRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.rejectBySeller(member.id(), refundRequestId, request.rejectReason()));
    }
}
```

Create `AdminRefundRequestController`:

```java
package com.sweet.market.refund.api;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sweet.market.auth.security.AuthenticatedMember;
import com.sweet.market.common.api.ApiResponse;
import com.sweet.market.refund.application.RefundRequestService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/refund-requests")
public class AdminRefundRequestController {

    private final RefundRequestService refundRequestService;

    public AdminRefundRequestController(RefundRequestService refundRequestService) {
        this.refundRequestService = refundRequestService;
    }

    @PostMapping("/{refundRequestId}/approve")
    public ApiResponse<RefundRequestResponse> approve(Authentication authentication, @PathVariable Long refundRequestId) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.approveByAdmin(member.id(), refundRequestId));
    }

    @PostMapping("/{refundRequestId}/reject")
    public ApiResponse<RefundRequestResponse> reject(
            Authentication authentication,
            @PathVariable Long refundRequestId,
            @Valid @RequestBody RefundRejectRequest request
    ) {
        AuthenticatedMember member = (AuthenticatedMember) authentication.getPrincipal();
        return ApiResponse.ok(refundRequestService.rejectByAdmin(member.id(), refundRequestId, request.rejectReason()));
    }
}
```

- [ ] **Step 5: Run seller/admin tests and commit**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*RefundRequestApiTest"
```

Expected: PASS.

Commit:

```powershell
git add backend/src/main/java/com/sweet/market/refund backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java
git commit -m "feat: handle refund approvals"
```

## Task 4: Immediate Paid Cancellation Through Order API

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/order/application/OrderService.java`
- Modify: `backend/src/test/java/com/sweet/market/order/OrderApiTest.java`

- [ ] **Step 1: Write failing order cancellation tests**

Update `OrderApiTest` by replacing `이미_취소한_주문은_다시_취소할_수_없다` with:

```java
@Test
void 이미_취소한_주문은_다시_취소해도_같은_결과를_반환한다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken);
    Long orderId = createOrder(buyerToken, productId);

    mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk());

    mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELED"))
            .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"));
}
```

Add:

```java
@Test
void 주문자는_결제완료_주문을_주문_API로_취소할_수_있다() throws Exception {
    String sellerToken = signupAndLogin("seller@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken);
    Long orderId = createOrder(buyerToken, productId);
    approvePayment(buyerToken, orderId);

    mockMvc.perform(post("/api/orders/{orderId}/cancel", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELED"))
            .andExpect(jsonPath("$.data.productStatus").value("ON_SALE"));
}
```

Add helper:

```java
private Long approvePayment(String accessToken, Long orderId) throws Exception {
    String response = mockMvc.perform(post("/api/payments/{orderId}/approve", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    JsonNode root = objectMapper.readTree(response);
    return root.path("data").path("id").asLong();
}
```

- [ ] **Step 2: Run cancellation tests and verify failure**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*OrderApiTest"
```

Expected: FAIL because `OrderService.cancel()` still calls only `Order.cancel()` and rejects `PAID`.

- [ ] **Step 3: Implement paid cancellation in OrderService**

Add `PaymentRepository` and `PaymentGateway` dependencies to `OrderService`, then replace `cancel()` with:

```java
@Transactional
public OrderResponse cancel(Long buyerId, Long orderId) {
    Order order = orderRepository.findWithBuyerAndProductById(orderId)
            .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));
    if (!order.isOwnedBy(buyerId)) {
        throw new BusinessException(ErrorCode.ORDER_ACCESS_DENIED);
    }

    try {
        if (order.getStatus() == OrderStatus.PAID) {
            Payment payment = paymentRepository.findWithOrderByOrderId(orderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
            paymentGateway.cancel(payment.getExternalPaymentId());
            payment.cancel();
        } else {
            order.cancel();
        }
    } catch (IllegalStateException exception) {
        throw new BusinessException(ErrorCode.ORDER_CANCEL_NOT_ALLOWED);
    }

    return OrderResponse.from(order);
}
```

Required imports:

```java
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.payment.application.PaymentGateway;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.repository.PaymentRepository;
```

- [ ] **Step 4: Run cancellation tests and commit**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*OrderApiTest" --tests "*PaymentApiTest"
```

Expected: PASS.

Commit:

```powershell
git add backend/src/main/java/com/sweet/market/order/application/OrderService.java backend/src/test/java/com/sweet/market/order/OrderApiTest.java
git commit -m "feat: cancel paid orders from order API"
```

## Task 5: Refund State In Order Reads

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/order/api/OrderResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/order/api/OrderSummaryResponse.java`
- Modify: `backend/src/main/java/com/sweet/market/order/query/OrderQueryService.java`
- Modify: `backend/src/main/java/com/sweet/market/refund/repository/RefundRequestRepository.java`
- Test: `backend/src/test/java/com/sweet/market/order/OrderQueryApiTest.java`

- [ ] **Step 1: Write failing order read tests**

Add to `OrderQueryApiTest`:

```java
@Test
void 내_주문_목록은_환불_요청_상태를_포함한다() throws Exception {
    String sellerToken = signupAndLogin("seller-refund-list@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-refund-list@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "Refund List Product");
    Long orderId = createDeliveredOrder(buyerToken, productId);
    createRefundRequest(buyerToken, orderId);

    mockMvc.perform(get("/api/orders/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].id").value(orderId))
            .andExpect(jsonPath("$.data.content[0].status").value("REFUND_REQUESTED"))
            .andExpect(jsonPath("$.data.content[0].refundStatus").value("REQUESTED"))
            .andExpect(jsonPath("$.data.content[0].refundRequestedAt").exists());
}
```

- [ ] **Step 2: Run order read tests and verify failure**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*OrderQueryApiTest"
```

Expected: FAIL because order read DTOs do not expose refund fields.

- [ ] **Step 3: Add refund summary repository query**

Add to `RefundRequestRepository`:

```java
import java.util.Collection;
import java.util.List;

List<RefundRequest> findByOrderIdIn(Collection<Long> orderIds);
```

- [ ] **Step 4: Add refund fields to order responses**

Change `OrderSummaryResponse` to include:

```java
String refundStatus,
LocalDateTime refundRequestedAt,
LocalDateTime refundHandledAt,
String refundRejectReason
```

Add overload:

```java
public static OrderSummaryResponse from(Order order, boolean reviewed, RefundRequest refundRequest) {
    return new OrderSummaryResponse(
            order.getId(),
            order.getProduct().getId(),
            order.getProduct().getTitle(),
            order.getProduct().getPrice(),
            order.getProduct().getSeller().getId(),
            order.getProduct().getSeller().getNickname(),
            order.getStatus().name(),
            order.getProduct().getStatus().name(),
            order.getOrderedAt(),
            reviewed,
            refundRequest == null ? null : refundRequest.getStatus().name(),
            refundRequest == null ? null : refundRequest.getRequestedAt(),
            refundRequest == null ? null : refundRequest.getHandledAt(),
            refundRequest == null ? null : refundRequest.getRejectReason()
    );
}
```

Change `OrderResponse.from` to accept nullable `RefundRequest`:

```java
public static OrderResponse from(Order order, RefundRequest refundRequest) {
    return new OrderResponse(
            order.getId(),
            order.getBuyer().getId(),
            order.getBuyer().getNickname(),
            order.getProduct().getId(),
            order.getProduct().getSeller().getId(),
            order.getProduct().getSeller().getNickname(),
            order.getProduct().getTitle(),
            order.getProduct().getPrice(),
            order.getStatus().name(),
            order.getProduct().getStatus().name(),
            order.getOrderedAt(),
            order.getCanceledAt(),
            refundRequest == null ? null : refundRequest.getStatus().name(),
            refundRequest == null ? null : refundRequest.getRequestedAt(),
            refundRequest == null ? null : refundRequest.getHandledAt(),
            refundRequest == null ? null : refundRequest.getRejectReason()
    );
}
```

Keep `OrderResponse.from(Order order)` delegating to `from(order, null)` so existing services compile:

```java
public static OrderResponse from(Order order) {
    return from(order, null);
}
```

- [ ] **Step 5: Attach refund summaries in OrderQueryService**

Add `RefundRequestRepository` constructor dependency. In `findMine`, load refund requests by order id and map them:

```java
Map<Long, RefundRequest> refundRequestsByOrderId = orderIds.isEmpty()
        ? Map.of()
        : refundRequestRepository.findByOrderIdIn(orderIds)
                .stream()
                .collect(Collectors.toMap(refundRequest -> refundRequest.getOrder().getId(), Function.identity()));

return orders.map(order -> OrderSummaryResponse.from(
        order,
        reviewedOrderIds.contains(order.getId()),
        refundRequestsByOrderId.get(order.getId())
));
```

In `findOne`, fetch refund request by order id:

```java
RefundRequest refundRequest = refundRequestRepository.findWithOrderByOrderId(orderId).orElse(null);
return OrderResponse.from(order, refundRequest);
```

- [ ] **Step 6: Run order read tests and commit**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*OrderQueryApiTest" --tests "*RefundRequestApiTest"
```

Expected: PASS.

Commit:

```powershell
git add backend/src/main/java/com/sweet/market/order/api/OrderResponse.java backend/src/main/java/com/sweet/market/order/api/OrderSummaryResponse.java backend/src/main/java/com/sweet/market/order/query/OrderQueryService.java backend/src/main/java/com/sweet/market/refund/repository/RefundRequestRepository.java backend/src/test/java/com/sweet/market/order/OrderQueryApiTest.java
git commit -m "feat: expose refund state on orders"
```

## Task 6: Confirmation And Settlement Blocking

**Files:**

- Modify: `backend/src/test/java/com/sweet/market/order/OrderConfirmApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/settlement/SettlementApiTest.java`
- Modify: `backend/src/test/java/com/sweet/market/settlement/batch/SettlementBatchJobTest.java`

- [ ] **Step 1: Add confirmation blocking test**

Add to `OrderConfirmApiTest`:

```java
@Test
void 환불_요청중인_주문은_구매확정할_수_없다() throws Exception {
    String sellerToken = signupAndLogin("seller-refund-confirm@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-refund-confirm@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "Refund Confirm Product");
    Long orderId = createDeliveredOrder(buyerToken, productId);
    createRefundRequest(buyerToken, orderId);

    mockMvc.perform(post("/api/orders/{orderId}/confirm", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + buyerToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ORDER_CONFIRM_NOT_ALLOWED"));
}
```

- [ ] **Step 2: Add settlement blocking tests**

Add to `SettlementApiTest`:

```java
@Test
void 환불_요청중인_주문은_정산할_수_없다() throws Exception {
    String sellerToken = signupAndLogin("seller-refund-settlement@example.com", "password123", "seller");
    String buyerToken = signupAndLogin("buyer-refund-settlement@example.com", "password123", "buyer");
    Long productId = createProduct(sellerToken, "Refund Settlement Product");
    Long orderId = createDeliveredOrder(buyerToken, productId);
    createRefundRequest(buyerToken, orderId);

    mockMvc.perform(post("/api/settlements/orders/{orderId}", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + sellerToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SETTLEMENT_CREATE_NOT_ALLOWED"));
}
```

Add helper if absent:

```java
private Long createRefundRequest(String accessToken, Long orderId) throws Exception {
    String response = mockMvc.perform(post("/api/orders/{orderId}/refund-requests", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
                            }
                            """))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).path("data").path("id").asLong();
}
```

- [ ] **Step 3: Add batch exclusion test**

Add to `SettlementBatchJobTest`:

```java
@Test
void 환불_요청중인_주문은_정산_배치_대상이_아니다() throws Exception {
    Order refundRequestedOrder = confirmedOrder();
    jdbcTemplate.update("update orders set status = 'REFUND_REQUESTED' where id = ?", refundRequestedOrder.getId());

    BatchRunResult result = launchSettlementJob(LocalDateTime.now().plusDays(1), 10, 5);

    assertThat(result.writeCount()).isZero();
    assertThat(settlementRepository.count()).isZero();
}
```

- [ ] **Step 4: Run blocking tests**

Run:

```powershell
cd backend
.\gradlew.bat test --tests "*OrderConfirmApiTest" --tests "*SettlementApiTest" --tests "*SettlementBatchJobTest"
```

Expected: PASS because existing domain/service rules already reject non-`DELIVERED` confirmation and non-`CONFIRMED` settlement, and batch SQL reads only `CONFIRMED`.

- [ ] **Step 5: Commit**

Commit:

```powershell
git add backend/src/test/java/com/sweet/market/order/OrderConfirmApiTest.java backend/src/test/java/com/sweet/market/settlement/SettlementApiTest.java backend/src/test/java/com/sweet/market/settlement/batch/SettlementBatchJobTest.java
git commit -m "test: cover refund settlement blocking"
```

## Task 7: Buyer Web Refund Flow

**Files:**

- Modify: `web/src/features/orders/orderApi.ts`
- Modify: `web/src/pages/MyOrdersPage.tsx`
- Modify: `web/src/shared/styles.css`

- [ ] **Step 1: Extend web API types**

Update `OrderStatus`:

```ts
export type OrderStatus =
  | 'CREATED'
  | 'PAID'
  | 'SHIPPING'
  | 'DELIVERED'
  | 'REFUND_REQUESTED'
  | 'REFUNDED'
  | 'CONFIRMED'
  | 'CANCELED';
```

Add refund types:

```ts
export type RefundRequestStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED';

export type RefundRequest = {
  id: number;
  orderId: number;
  productId: number;
  productTitle: string;
  buyerId: number;
  reason: string;
  status: RefundRequestStatus;
  requestedAt: string;
  handledById: number | null;
  handledAt: string | null;
  rejectReason: string | null;
};
```

Add to `OrderSummary`:

```ts
refundStatus: RefundRequestStatus | null;
refundRequestedAt: string | null;
refundHandledAt: string | null;
refundRejectReason: string | null;
```

Add API call:

```ts
export function createRefundRequest(orderId: number, reason: string) {
  return api<RefundRequest>(`/api/orders/${orderId}/refund-requests`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  });
}
```

- [ ] **Step 2: Add refund mutation and form state**

In `MyOrdersPage.tsx`, extend imports:

```ts
import { cancelOrder, confirmOrder, createRefundRequest, getMyOrders, type OrderSummary } from '../features/orders/orderApi';
```

Extend `OrderAction`:

```ts
type OrderAction =
  | 'approve-payment'
  | 'cancel-order'
  | 'cancel-payment'
  | 'start-delivery'
  | 'complete-delivery'
  | 'confirm-order'
  | 'request-refund';
```

Add state:

```ts
const [refundingOrderId, setRefundingOrderId] = useState<number | null>(null);
const [refundReason, setRefundReason] = useState('');
```

Add mutation:

```ts
const refundMutation = useMutation({
  mutationFn: (order: OrderSummary) => createRefundRequest(order.id, refundReason),
  onSuccess: async (_refundRequest, order) => {
    resetRefundForm();
    await invalidateOrderResources(queryClient, order.productId);
  },
});
```

Include `refundMutation.error` in `actionError`.

- [ ] **Step 3: Add refund form helpers**

Add functions:

```ts
function startRefund(orderId: number) {
  refundMutation.reset();
  setRefundingOrderId(orderId);
  setRefundReason('');
}

function resetRefundForm() {
  setRefundingOrderId(null);
  setRefundReason('');
  refundMutation.reset();
}

function submitRefund(event: FormEvent<HTMLFormElement>, order: OrderSummary) {
  event.preventDefault();

  if (refundMutation.isPending) {
    return;
  }

  refundMutation.mutate(order);
}
```

- [ ] **Step 4: Update order actions**

In `renderOrderActions`, change `PAID` cancellation button to use order cancellation:

```tsx
<button
  type="button"
  className="text-button danger-button"
  disabled={cancelPaymentPending}
  onClick={() => runOrderAction(order, 'cancel-payment', cancelOrderMutation.mutateAsync)}
>
  주문 취소
</button>
```

In `DELIVERED`, show confirm and refund entry:

```tsx
case 'DELIVERED':
  const confirmPending = isOrderActionPending(order.id, 'confirm-order');

  return (
    <>
      <button
        type="button"
        className="text-button"
        disabled={confirmPending}
        onClick={() => runOrderAction(order, 'confirm-order', confirmMutation.mutateAsync)}
      >
        구매 확정
      </button>
      {order.refundStatus === null ? (
        <button
          type="button"
          className="text-button danger-button"
          onClick={() => startRefund(order.id)}
        >
          환불 요청
        </button>
      ) : null}
    </>
  );
case 'REFUND_REQUESTED':
  return <span className="muted-text">환불 처리 대기 중</span>;
case 'REFUNDED':
  return <span className="muted-text">환불 완료</span>;
```

- [ ] **Step 5: Render refund status and form**

Below `<div className="record-actions">{renderOrderActions(order)}</div>`, add:

```tsx
{order.refundStatus ? (
  <p className="record-note">
    환불 상태: {formatRefundStatus(order.refundStatus)}
    {order.refundRejectReason ? ` · 거절 사유: ${order.refundRejectReason}` : ''}
  </p>
) : null}
{refundingOrderId === order.id ? (
  <form className="review-form" onSubmit={(event) => submitRefund(event, order)}>
    <label>
      환불 사유
      <textarea
        value={refundReason}
        minLength={10}
        maxLength={500}
        required
        rows={4}
        disabled={refundMutation.isPending}
        onChange={(event) => setRefundReason(event.target.value)}
      />
    </label>
    <div className="review-form-actions">
      <button type="submit" className="text-button danger-button" disabled={refundMutation.isPending}>
        {refundMutation.isPending ? '요청 중' : '요청'}
      </button>
      <button
        type="button"
        className="text-button secondary-button"
        disabled={refundMutation.isPending}
        onClick={resetRefundForm}
      >
        취소
      </button>
    </div>
  </form>
) : null}
```

Add formatter cases:

```ts
case 'REFUND_REQUESTED':
  return '환불 요청';
case 'REFUNDED':
  return '환불 완료';
```

Add helper:

```ts
function formatRefundStatus(status: string) {
  switch (status) {
    case 'REQUESTED':
      return '요청됨';
    case 'APPROVED':
      return '승인됨';
    case 'REJECTED':
      return '거절됨';
    default:
      return status;
  }
}
```

- [ ] **Step 6: Build web and commit**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

Commit:

```powershell
git add web/src/features/orders/orderApi.ts web/src/pages/MyOrdersPage.tsx web/src/shared/styles.css
git commit -m "feat: add buyer refund request UI"
```

## Task 8: Full Verification And Handoff

**Files:**

- Create: `docs/superpowers/handoffs/2026-07-07-milestone-18-cancellation-and-refund-flow-handoff.md`

- [ ] **Step 1: Run full backend suite**

Run:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```

Expected: PASS.

- [ ] **Step 2: Run web build**

Run:

```powershell
cd web
npm run build
```

Expected: PASS.

- [ ] **Step 3: Run repository hygiene check**

Run:

```powershell
git diff --check
```

Expected: no output.

- [ ] **Step 4: Write handoff**

Create `docs/superpowers/handoffs/2026-07-07-milestone-18-cancellation-and-refund-flow-handoff.md`:

```markdown
# Milestone 18 Cancellation And Refund Flow Handoff

## Completed

- Added delivered-order refund requests.
- Added seller and admin refund approval/rejection.
- Added pre-delivery paid order cancellation through the order API.
- Added refund-aware order states and payment refund state.
- Blocked purchase confirmation while refund is requested.
- Kept refund-requested and refunded orders out of settlement.
- Added buyer My Orders refund request UI.

## Verification

- Backend full suite passed:
  - `cd backend`
  - `$env:JAVA_HOME='C:\java\jdk-21'`
  - `$env:PATH="$env:JAVA_HOME\bin;$env:PATH"`
  - `$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`
  - `.\gradlew.bat --no-daemon test`
- Web build passed:
  - `cd web`
  - `npm run build`
- Repo checks passed:
  - `git diff --check`

## Local Notes

- `backend/src/main/resources/application.yaml` has a pre-existing local-only development change in the main checkout and was not touched by this work.

## Follow-Up Candidates

- Dedicated seller refund queue.
- Dedicated admin refund management page.
- Return shipping workflow.
- Refund request cancellation by buyer.
- Product relisting after refund.
- Refund-related review rules.
```

- [ ] **Step 5: Commit handoff**

Commit:

```powershell
git add docs/superpowers/handoffs/2026-07-07-milestone-18-cancellation-and-refund-flow-handoff.md
git commit -m "docs: add milestone 18 handoff"
```

## Self-Review Checklist

- Spec coverage: Tasks cover immediate cancellation, buyer refund request creation, seller/admin approval/rejection, duplicate prevention, confirmation blocking, settlement blocking, buyer web flow, and verification.
- Scope control: Dedicated seller/admin refund queues, return shipping, relisting, review rules, and dispute workflow are not implemented.
- Type consistency: `RefundRequestStatus` uses `REQUESTED`, `APPROVED`, `REJECTED`; `OrderStatus` uses `REFUND_REQUESTED`, `REFUNDED`; `PaymentStatus` uses `REFUNDED`.
- Existing local change protection: no step stages or edits `backend/src/main/resources/application.yaml`.
