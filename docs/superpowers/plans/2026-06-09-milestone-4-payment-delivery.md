# Milestone 4 Payment And Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 주문 생성 이후 결제 승인/취소와 배송 시작/완료 API를 추가해 거래 흐름을 `CREATED -> PAID -> SHIPPING -> DELIVERED`까지 진행시킨다.

**Architecture:** `payment`와 `delivery` 패키지는 기존 `order` 패키지와 같은 `api/application/domain/repository` 구조를 따른다. 외부 연동은 `PaymentGateway`, `DeliveryClient` 인터페이스 뒤에 두고 첫 구현은 fake adapter로 처리한다. 주문 상태 전이는 `Order` aggregate가 소유하고, `PaymentService`와 `DeliveryService`는 인증 사용자 권한, 연동 호출, 저장 트랜잭션을 조율한다.

**Tech Stack:** Spring Boot, Spring MVC, Spring Security, Spring Data JPA, PostgreSQL, Lombok, JUnit 5, MockMvc, Testcontainers

---

## Scope

완료 기준:

- 인증된 주문자는 `CREATED` 주문의 결제를 승인할 수 있다.
- 결제 승인 시 `Payment`가 `APPROVED` 상태로 저장되고 주문은 `PAID` 상태가 된다.
- 인증된 주문자는 `PAID` 주문의 결제를 취소할 수 있다.
- 결제 취소 시 `Payment`는 `CANCELED`, 주문은 `CANCELED`, 상품은 `ON_SALE` 상태가 된다.
- 인증된 주문자는 `PAID` 주문의 배송을 시작할 수 있다.
- 배송 시작 시 `Delivery`가 `SHIPPING` 상태로 저장되고 주문은 `SHIPPING` 상태가 된다.
- 인증된 주문자는 `SHIPPING` 주문의 배송을 완료할 수 있다.
- 배송 완료 시 `Delivery`는 `DELIVERED`, 주문은 `DELIVERED` 상태가 된다.
- 모든 신규 JUnit `@Test` 메서드명은 Korean_with_underscores 형식을 따른다.

Out of scope:

- 실제 결제사/배송사 API 연동
- 배송 주소, 운송장 번호, 결제 금액 검증
- 구매 확정, 상품 `SOLD_OUT` 전이, 정산
- 판매자/관리자 배송 처리 권한 모델
- 주문 목록/상세 조회 API

## File Structure

생성 또는 수정할 파일:

```text
backend/src/main/java/com/sweet/market/common/error/ErrorCode.java

backend/src/main/java/com/sweet/market/order/domain/Order.java

backend/src/main/java/com/sweet/market/payment/domain/Payment.java
backend/src/main/java/com/sweet/market/payment/domain/PaymentStatus.java
backend/src/main/java/com/sweet/market/payment/repository/PaymentRepository.java
backend/src/main/java/com/sweet/market/payment/application/PaymentGateway.java
backend/src/main/java/com/sweet/market/payment/application/FakePaymentGateway.java
backend/src/main/java/com/sweet/market/payment/application/PaymentService.java
backend/src/main/java/com/sweet/market/payment/api/PaymentController.java
backend/src/main/java/com/sweet/market/payment/api/PaymentResponse.java

backend/src/main/java/com/sweet/market/delivery/domain/Delivery.java
backend/src/main/java/com/sweet/market/delivery/domain/DeliveryStatus.java
backend/src/main/java/com/sweet/market/delivery/repository/DeliveryRepository.java
backend/src/main/java/com/sweet/market/delivery/application/DeliveryClient.java
backend/src/main/java/com/sweet/market/delivery/application/FakeDeliveryClient.java
backend/src/main/java/com/sweet/market/delivery/application/DeliveryService.java
backend/src/main/java/com/sweet/market/delivery/api/DeliveryController.java
backend/src/main/java/com/sweet/market/delivery/api/DeliveryResponse.java

backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
backend/src/test/java/com/sweet/market/order/domain/OrderTest.java
backend/src/test/java/com/sweet/market/payment/domain/PaymentTest.java
backend/src/test/java/com/sweet/market/payment/PaymentApiTest.java
backend/src/test/java/com/sweet/market/delivery/domain/DeliveryTest.java
backend/src/test/java/com/sweet/market/delivery/DeliveryApiTest.java
```

## Task 1: 주문 상태 전이 추가

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/order/domain/Order.java`
- Modify: `backend/src/test/java/com/sweet/market/order/domain/OrderTest.java`

- [ ] **Step 1: 실패하는 주문 상태 전이 테스트를 추가한다**

`OrderTest`에 결제/배송 상태 전이 테스트를 추가한다.

```java
@Test
void 생성된_주문을_결제완료로_바꾼다() {
    Order order = createOrder();

    order.markPaid();

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
}

@Test
void 결제완료_주문을_배송중으로_바꾼다() {
    Order order = createOrder();
    order.markPaid();

    order.startShipping();

    assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPING);
}

@Test
void 배송중_주문을_배송완료로_바꾼다() {
    Order order = createOrder();
    order.markPaid();
    order.startShipping();

    order.completeDelivery();

    assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
}
```

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.order.domain.OrderTest"
```

Expected: `markPaid`, `startShipping`, `completeDelivery` 메서드가 없어서 컴파일 실패한다.

- [ ] **Step 3: `Order`에 상태 전이 메서드를 추가한다**

```java
public void markPaid() {
    if (status != OrderStatus.CREATED) {
        throw new IllegalStateException("Order cannot be paid: " + status);
    }
    this.status = OrderStatus.PAID;
}

public void cancelPaidOrder() {
    if (status != OrderStatus.PAID) {
        throw new IllegalStateException("Paid order cannot be canceled: " + status);
    }
    product.restoreOnSaleFromReservation();
    this.status = OrderStatus.CANCELED;
    this.canceledAt = LocalDateTime.now();
}

public void startShipping() {
    if (status != OrderStatus.PAID) {
        throw new IllegalStateException("Order cannot start shipping: " + status);
    }
    this.status = OrderStatus.SHIPPING;
}

public void completeDelivery() {
    if (status != OrderStatus.SHIPPING) {
        throw new IllegalStateException("Order cannot complete delivery: " + status);
    }
    this.status = OrderStatus.DELIVERED;
}
```

- [ ] **Step 4: 주문 도메인 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.order.domain.OrderTest"
```

Expected: `BUILD SUCCESSFUL`

## Task 2: 결제 도메인과 fake gateway 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/payment/domain/PaymentStatus.java`
- Create: `backend/src/main/java/com/sweet/market/payment/domain/Payment.java`
- Create: `backend/src/main/java/com/sweet/market/payment/application/PaymentGateway.java`
- Create: `backend/src/main/java/com/sweet/market/payment/application/FakePaymentGateway.java`
- Create: `backend/src/main/java/com/sweet/market/payment/repository/PaymentRepository.java`
- Create: `backend/src/test/java/com/sweet/market/payment/domain/PaymentTest.java`

- [ ] **Step 1: 실패하는 결제 도메인 테스트를 작성한다**

`PaymentTest`는 `Payment.approve(order, externalPaymentId)`가 승인된 결제를 만들고 주문을 `PAID`로 바꾸는지 검증한다.

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.payment.domain.PaymentTest"
```

Expected: `Payment` 패키지가 없어서 컴파일 실패한다.

- [ ] **Step 3: 결제 도메인 최소 구현을 추가한다**

`PaymentStatus`는 `READY`, `APPROVED`, `CANCELED`, `FAILED`를 가진다. `Payment.approve(Order order, String externalPaymentId)`는 주문을 `markPaid()`로 전이시키고 `APPROVED` 결제를 반환한다. `Payment.cancel()`은 결제 상태를 `CANCELED`로 바꾸고 주문의 `cancelPaidOrder()`를 호출한다.

- [ ] **Step 4: 결제 도메인 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.payment.domain.PaymentTest"
```

Expected: `BUILD SUCCESSFUL`

## Task 3: 결제 API 추가

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Create: `backend/src/main/java/com/sweet/market/payment/application/PaymentService.java`
- Create: `backend/src/main/java/com/sweet/market/payment/api/PaymentController.java`
- Create: `backend/src/main/java/com/sweet/market/payment/api/PaymentResponse.java`
- Create: `backend/src/test/java/com/sweet/market/payment/PaymentApiTest.java`

- [ ] **Step 1: 실패하는 결제 API 테스트를 작성한다**

테스트는 `POST /api/payments/{orderId}/approve` 성공, 미인증 실패, 타 사용자 실패, 중복 승인 실패, `POST /api/payments/{orderId}/cancel` 성공을 검증한다.

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.payment.PaymentApiTest"
```

Expected: 결제 API가 없어 404 또는 컴파일 실패가 발생한다.

- [ ] **Step 3: 결제 서비스와 컨트롤러를 구현한다**

추가 에러 코드는 다음을 사용한다.

```java
PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제를 찾을 수 없습니다."),
PAYMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "결제에 대한 권한이 없습니다."),
PAYMENT_APPROVE_NOT_ALLOWED(HttpStatus.CONFLICT, "승인할 수 없는 주문 상태입니다."),
PAYMENT_CANCEL_NOT_ALLOWED(HttpStatus.CONFLICT, "취소할 수 없는 결제 상태입니다."),
```

- [ ] **Step 4: 결제 API 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.payment.PaymentApiTest"
```

Expected: `BUILD SUCCESSFUL`

## Task 4: 배송 도메인과 fake client 추가

**Files:**

- Create: `backend/src/main/java/com/sweet/market/delivery/domain/DeliveryStatus.java`
- Create: `backend/src/main/java/com/sweet/market/delivery/domain/Delivery.java`
- Create: `backend/src/main/java/com/sweet/market/delivery/application/DeliveryClient.java`
- Create: `backend/src/main/java/com/sweet/market/delivery/application/FakeDeliveryClient.java`
- Create: `backend/src/main/java/com/sweet/market/delivery/repository/DeliveryRepository.java`
- Create: `backend/src/test/java/com/sweet/market/delivery/domain/DeliveryTest.java`

- [ ] **Step 1: 실패하는 배송 도메인 테스트를 작성한다**

`Delivery.start(order, trackingNumber)`가 주문을 `SHIPPING`으로 바꾸고, `complete()`가 주문을 `DELIVERED`로 바꾸는지 검증한다.

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.delivery.domain.DeliveryTest"
```

Expected: `Delivery` 패키지가 없어서 컴파일 실패한다.

- [ ] **Step 3: 배송 도메인 최소 구현을 추가한다**

`DeliveryStatus`는 `READY`, `SHIPPING`, `DELIVERED`를 가진다. `Delivery.start(Order order, String trackingNumber)`는 주문의 `startShipping()`을 호출하고 `SHIPPING` 배송을 반환한다. `Delivery.complete()`은 주문의 `completeDelivery()`를 호출하고 배송을 `DELIVERED`로 바꾼다.

- [ ] **Step 4: 배송 도메인 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.delivery.domain.DeliveryTest"
```

Expected: `BUILD SUCCESSFUL`

## Task 5: 배송 API 추가

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Create: `backend/src/main/java/com/sweet/market/delivery/application/DeliveryService.java`
- Create: `backend/src/main/java/com/sweet/market/delivery/api/DeliveryController.java`
- Create: `backend/src/main/java/com/sweet/market/delivery/api/DeliveryResponse.java`
- Create: `backend/src/test/java/com/sweet/market/delivery/DeliveryApiTest.java`

- [ ] **Step 1: 실패하는 배송 API 테스트를 작성한다**

테스트는 `POST /api/deliveries/{orderId}/start` 성공, 미결제 주문 배송 시작 실패, 타 사용자 실패, `POST /api/deliveries/{orderId}/complete` 성공을 검증한다.

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.delivery.DeliveryApiTest"
```

Expected: 배송 API가 없어 404 또는 컴파일 실패가 발생한다.

- [ ] **Step 3: 배송 서비스와 컨트롤러를 구현한다**

추가 에러 코드는 다음을 사용한다.

```java
DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "배송을 찾을 수 없습니다."),
DELIVERY_ACCESS_DENIED(HttpStatus.FORBIDDEN, "배송에 대한 권한이 없습니다."),
DELIVERY_START_NOT_ALLOWED(HttpStatus.CONFLICT, "배송을 시작할 수 없는 주문 상태입니다."),
DELIVERY_COMPLETE_NOT_ALLOWED(HttpStatus.CONFLICT, "배송을 완료할 수 없는 상태입니다."),
```

- [ ] **Step 4: 배송 API 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.delivery.DeliveryApiTest"
```

Expected: `BUILD SUCCESSFUL`

## Task 6: 전체 검증

**Files:**

- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`

- [ ] **Step 1: 테스트 DB 정리 테이블을 확장한다**

`IntegrationTestSupport.cleanUp()`의 TRUNCATE 대상에 `payments`, `deliveries`를 포함한다.

- [ ] **Step 2: 전체 테스트를 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 테스트 이름 규칙을 확인한다**

Run:

```powershell
cd ..
rg -n "void [a-zA-Z0-9]+\(" backend\src\test\java
```

Expected: `@Test`가 아닌 helper/lifecycle 메서드만 출력된다.

## Self-Review

- Spec coverage: fake `PaymentGateway`, 결제 승인/취소, fake `DeliveryClient`, 배송 시작/완료, 주문 상태 전이 테스트가 Task 1-5에 포함되어 있다.
- Placeholder scan: `TBD`, `TODO`, `implement later`, `fill in details` 문구를 사용하지 않았다.
- Type consistency: `Payment`, `PaymentStatus`, `PaymentRepository`, `PaymentService`, `PaymentResponse`, `Delivery`, `DeliveryStatus`, `DeliveryRepository`, `DeliveryService`, `DeliveryResponse` 이름과 패키지가 모든 Task에서 일치한다.
- Test naming: 모든 신규 테스트 예시는 Korean_with_underscores 형식이다.
- Scope check: 실제 외부 API, 구매 확정, 정산, 상품 `SOLD_OUT` 전이는 Milestone 4 범위 밖으로 남겼다.
