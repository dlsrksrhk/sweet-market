# Milestone 5 Confirm And Settlement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 배송 완료된 주문을 구매 확정하고, 확정된 주문을 기준으로 판매자 정산을 생성/조회한다.

**Architecture:** 구매 확정 상태 전이는 `Order` aggregate가 소유하고 상품 판매 완료 전이는 `Product`가 소유한다. `settlement` 패키지는 `api/application/domain/repository/query` 구조를 따르며, `Settlement`는 주문과 1:1로 연결되어 중복 정산을 막는다. 판매자 정산 조회는 쓰기 서비스와 분리한 `SettlementQueryService`가 담당한다.

**Tech Stack:** Spring Boot, Spring MVC, Spring Security, Spring Data JPA, PostgreSQL, Lombok, JUnit 5, MockMvc, Testcontainers

---

## Scope

완료 기준:

- 인증된 주문자는 배송 완료 주문을 구매 확정할 수 있다.
- 구매 확정 시 주문은 `CONFIRMED`, 상품은 `SOLD_OUT` 상태가 된다.
- 배송 완료 전 주문은 구매 확정할 수 없다.
- 주문자가 아닌 사용자는 구매 확정할 수 없다.
- 판매자는 확정된 주문에 대해 단건 정산을 생성할 수 있다.
- 정산 생성 시 주문 상품 가격을 정산 금액으로 저장하고 상태는 `COMPLETED`가 된다.
- 확정되지 않은 주문은 정산할 수 없다.
- 같은 주문은 두 번 정산할 수 없다.
- 판매자는 자기 상품의 정산 목록만 조회할 수 있다.
- 모든 신규 JUnit `@Test` 메서드명은 Korean_with_underscores 형식을 따른다.

Out of scope:

- 정산 수수료, 지급 계좌, 정산 실패/재시도
- 관리자 정산 승인
- 배치 정산
- 구매 확정 자동화
- 주문 목록/상세 조회 최적화

## File Structure

생성 또는 수정할 파일:

```text
backend/src/main/java/com/sweet/market/common/error/ErrorCode.java

backend/src/main/java/com/sweet/market/product/domain/Product.java

backend/src/main/java/com/sweet/market/order/domain/Order.java
backend/src/main/java/com/sweet/market/order/application/OrderService.java
backend/src/main/java/com/sweet/market/order/api/OrderController.java

backend/src/main/java/com/sweet/market/settlement/domain/Settlement.java
backend/src/main/java/com/sweet/market/settlement/domain/SettlementStatus.java
backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java
backend/src/main/java/com/sweet/market/settlement/application/SettlementService.java
backend/src/main/java/com/sweet/market/settlement/query/SettlementQueryService.java
backend/src/main/java/com/sweet/market/settlement/api/SettlementController.java
backend/src/main/java/com/sweet/market/settlement/api/SettlementResponse.java

backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java
backend/src/test/java/com/sweet/market/product/domain/ProductTest.java
backend/src/test/java/com/sweet/market/order/domain/OrderTest.java
backend/src/test/java/com/sweet/market/order/OrderConfirmApiTest.java
backend/src/test/java/com/sweet/market/settlement/domain/SettlementTest.java
backend/src/test/java/com/sweet/market/settlement/SettlementApiTest.java
```

## Task 1: 상품 판매 완료와 주문 구매 확정 상태 전이

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/product/domain/Product.java`
- Modify: `backend/src/main/java/com/sweet/market/order/domain/Order.java`
- Modify: `backend/src/test/java/com/sweet/market/product/domain/ProductTest.java`
- Modify: `backend/src/test/java/com/sweet/market/order/domain/OrderTest.java`

- [ ] **Step 1: 실패하는 도메인 테스트를 추가한다**

`ProductTest`에는 예약 상품을 판매 완료로 바꾸는 테스트를 추가한다. `OrderTest`에는 배송 완료 주문을 구매 확정하면 주문은 `CONFIRMED`, 상품은 `SOLD_OUT`이 되는 테스트를 추가한다.

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.domain.ProductTest" --tests "com.sweet.market.order.domain.OrderTest"
```

Expected: `Product.markSoldOutFromReservation()`과 `Order.confirm()`이 없어 컴파일 실패한다.

- [ ] **Step 3: 최소 구현을 추가한다**

`Product.markSoldOutFromReservation()`은 `RESERVED` 상품만 `SOLD_OUT`으로 바꾼다. `Order.confirm()`은 `DELIVERED` 주문만 `CONFIRMED`로 바꾸고 상품의 판매 완료 전이를 호출한다.

- [ ] **Step 4: 도메인 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.product.domain.ProductTest" --tests "com.sweet.market.order.domain.OrderTest"
```

Expected: `BUILD SUCCESSFUL`

## Task 2: 구매 확정 API

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Modify: `backend/src/main/java/com/sweet/market/order/application/OrderService.java`
- Modify: `backend/src/main/java/com/sweet/market/order/api/OrderController.java`
- Create: `backend/src/test/java/com/sweet/market/order/OrderConfirmApiTest.java`

- [ ] **Step 1: 실패하는 API 테스트를 작성한다**

테스트는 `POST /api/orders/{orderId}/confirm` 성공, 미배송 주문 실패, 타 사용자 실패를 검증한다.

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.order.OrderConfirmApiTest"
```

Expected: API가 없어 404 또는 컴파일 실패가 발생한다.

- [ ] **Step 3: 서비스와 컨트롤러를 구현한다**

추가 에러 코드는 다음을 사용한다.

```java
ORDER_CONFIRM_NOT_ALLOWED(HttpStatus.CONFLICT, "구매 확정할 수 없는 주문 상태입니다.")
```

`OrderService.confirm(memberId, orderId)`는 주문자 권한을 확인하고 `order.confirm()`을 호출한다.

- [ ] **Step 4: API 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.order.OrderConfirmApiTest"
```

Expected: `BUILD SUCCESSFUL`

## Task 3: 정산 도메인

**Files:**

- Create: `backend/src/main/java/com/sweet/market/settlement/domain/SettlementStatus.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/domain/Settlement.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/repository/SettlementRepository.java`
- Create: `backend/src/test/java/com/sweet/market/settlement/domain/SettlementTest.java`

- [ ] **Step 1: 실패하는 정산 도메인 테스트를 작성한다**

`Settlement.create(order)`가 확정 주문의 판매자, 주문, 상품 가격을 저장하고 `COMPLETED` 상태가 되는지 검증한다. 미확정 주문은 정산할 수 없어야 한다.

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.settlement.domain.SettlementTest"
```

Expected: `Settlement` 패키지가 없어 컴파일 실패한다.

- [ ] **Step 3: 정산 도메인 최소 구현을 추가한다**

`SettlementStatus`는 `READY`, `COMPLETED`, `FAILED`를 가진다. `Settlement.create(Order order)`는 `CONFIRMED` 주문만 허용하고, `seller`, `amount`, `settledAt`을 캡처한다.

- [ ] **Step 4: 정산 도메인 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.settlement.domain.SettlementTest"
```

Expected: `BUILD SUCCESSFUL`

## Task 4: 정산 API와 판매자 조회

**Files:**

- Modify: `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/application/SettlementService.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/query/SettlementQueryService.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/api/SettlementController.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/api/SettlementResponse.java`
- Create: `backend/src/test/java/com/sweet/market/settlement/SettlementApiTest.java`

- [ ] **Step 1: 실패하는 정산 API 테스트를 작성한다**

테스트는 `POST /api/settlements/orders/{orderId}` 성공, 미확정 주문 실패, 중복 정산 실패, 판매자가 아닌 사용자 실패, `GET /api/settlements/me` 판매자별 조회를 검증한다.

- [ ] **Step 2: 실패를 확인한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.settlement.SettlementApiTest"
```

Expected: 정산 API가 없어 404 또는 컴파일 실패가 발생한다.

- [ ] **Step 3: 정산 서비스와 컨트롤러를 구현한다**

추가 에러 코드는 다음을 사용한다.

```java
SETTLEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "정산을 찾을 수 없습니다."),
SETTLEMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "정산에 대한 권한이 없습니다."),
SETTLEMENT_CREATE_NOT_ALLOWED(HttpStatus.CONFLICT, "정산할 수 없는 주문 상태입니다."),
DUPLICATE_SETTLEMENT(HttpStatus.CONFLICT, "이미 정산된 주문입니다.")
```

`SettlementService.create(memberId, orderId)`는 판매자 권한, 중복 여부, 주문 확정 상태를 확인한다. `SettlementQueryService.findMine(memberId)`는 판매자의 정산만 최신순으로 반환한다.

- [ ] **Step 4: 정산 API 테스트를 통과시킨다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests "com.sweet.market.settlement.SettlementApiTest"
```

Expected: `BUILD SUCCESSFUL`

## Task 5: 전체 검증

**Files:**

- Modify: `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`

- [ ] **Step 1: 테스트 DB 정리 테이블을 확장한다**

`IntegrationTestSupport.cleanUp()`의 TRUNCATE 대상에 `settlements`를 포함한다.

- [ ] **Step 2: 전체 테스트를 실행한다**

Run:

```powershell
cd backend
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --rerun-tasks
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

- Spec coverage: 구매 확정, 상품 `SOLD_OUT` 전이, 단건 정산 생성, 중복 정산 방지, 판매자 정산 조회가 Task 1-4에 포함되어 있다.
- Placeholder scan: `TBD`, `TODO`, `implement later`, `fill in details` 문구를 사용하지 않았다.
- Type consistency: `Settlement`, `SettlementStatus`, `SettlementRepository`, `SettlementService`, `SettlementQueryService`, `SettlementResponse` 이름과 패키지가 모든 Task에서 일치한다.
- Test naming: 모든 신규 테스트 예시는 Korean_with_underscores 형식이다.
- Scope check: 배치 정산, 자동 구매 확정, 주문 조회 최적화는 Milestone 5 범위 밖으로 남겼다.
