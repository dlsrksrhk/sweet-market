# Domain Exception Structure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace direct standard exceptions in domain models with typed domain errors while preserving all existing API error responses.

**Architecture:** A dependency-free `DomainException` carries a domain-owned enum implementing `DomainError`. Each application use case maps that exception to the existing `BusinessException(ErrorCode)` with the original exception as cause. `GlobalExceptionHandler`, `ErrorResponse`, HTTP statuses, external codes, and Korean messages are unchanged.

**Tech Stack:** Java 21, Spring Boot, JPA, JUnit 5, AssertJ, Gradle, TypeScript.

## Global Constraints

- Run Gradle with JDK 21 and `JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`.
- New JUnit test names use Korean with underscores.
- Do not change `ErrorResponse`, `ErrorCode`, existing HTTP statuses, or web source.
- Exclude configuration, JWT parsing, framework, persistence, and file-storage exceptions.
- Catch `DomainException` only; never catch general runtime exceptions.

---

### Task 1: Add the common exception contract

**Files:**
- Create: `backend/src/main/java/com/sweet/market/common/domain/error/DomainError.java`
- Create: `backend/src/main/java/com/sweet/market/common/domain/error/DomainException.java`
- Modify: `backend/src/main/java/com/sweet/market/common/error/BusinessException.java`
- Test: `backend/src/test/java/com/sweet/market/common/domain/error/DomainExceptionTest.java`

- [ ] **Step 1: Write the failing contract tests**

```java
@Test
void 도메인_예외는_오류_코드를_보존한다() {
    DomainException exception = new DomainException(TestError.INVALID);
    assertThat(exception.error()).isEqualTo(TestError.INVALID);
}

@Test
void 비즈니스_예외는_원인_예외를_보존한다() {
    RuntimeException cause = new RuntimeException();
    assertThat(new BusinessException(ErrorCode.VALIDATION_ERROR, cause).getCause()).isSameAs(cause);
}

private enum TestError implements DomainError { INVALID }
```

- [ ] **Step 2: Run red test**

Run: `cd backend; .\gradlew.bat test --tests com.sweet.market.common.domain.error.DomainExceptionTest`

Expected: compilation fails because the types and constructor do not exist.

- [ ] **Step 3: Implement the smallest API**

```java
public interface DomainError { }

public final class DomainException extends RuntimeException {
    private final DomainError error;
    public DomainException(DomainError error) { super(error.toString()); this.error = error; }
    public DomainError error() { return error; }
}

public BusinessException(ErrorCode errorCode, Throwable cause) {
    super(errorCode.message(), cause);
    this.errorCode = errorCode;
}
```

- [ ] **Step 4: Run green test and commit**

Run the Step 2 command. Expected: 2 tests pass.

```powershell
git add backend/src/main/java/com/sweet/market/common/domain/error backend/src/main/java/com/sweet/market/common/error/BusinessException.java backend/src/test/java/com/sweet/market/common/domain/error/DomainExceptionTest.java
git commit -m "feat: add typed domain exception contract"
```

### Task 2: Convert product and inventory domain rules

**Files:**
- Create: `backend/src/main/java/com/sweet/market/product/domain/ProductDomainError.java`
- Create: `backend/src/main/java/com/sweet/market/inventory/domain/InventoryDomainError.java`
- Modify: `backend/src/main/java/com/sweet/market/product/domain/Product.java`
- Modify: `backend/src/main/java/com/sweet/market/inventory/domain/Inventory.java`
- Test: `backend/src/test/java/com/sweet/market/product/domain/ProductTest.java`
- Test: `backend/src/test/java/com/sweet/market/inventory/domain/InventoryTest.java`

- [ ] **Step 1: Replace message assertions with failing typed assertions**

```java
assertThatThrownBy(() -> product.replaceImages(List.of()))
        .isInstanceOf(DomainException.class)
        .extracting(exception -> ((DomainException) exception).error())
        .isEqualTo(ProductDomainError.IMAGE_REQUIRED);
```

Create these exact enums:

```java
enum ProductDomainError implements DomainError {
    IMAGE_NOT_FOUND, IMAGE_REQUIRED, IMAGE_LIMIT_EXCEEDED,
    REPRESENTATIVE_IMAGE_COUNT_INVALID, IMAGE_SORT_ORDER_DUPLICATE,
    CHANGE_NOT_ALLOWED, NOT_HIDDEN, NOT_ON_SALE, NOT_RESERVED,
    STOCK_SETTINGS_UNAVAILABLE, LOW_STOCK_THRESHOLD_INVALID,
    SALES_POLICY_REQUIRED, SINGLE_ITEM_STOCK_SETTINGS_INVALID,
    INITIAL_STOCK_QUANTITY_INVALID
}
enum InventoryDomainError implements DomainError {
    SINGLE_ITEM_PRODUCT_NOT_SUPPORTED, STOCK_UNAVAILABLE, RESERVATION_NOT_FOUND,
    TOTAL_BELOW_RESERVED_QUANTITY, ADJUSTMENT_REASON_REQUIRED,
    ADJUSTMENT_ACTOR_REQUIRED, TOTAL_QUANTITY_NEGATIVE, ORDER_PRODUCT_MISMATCH
}
```

- [ ] **Step 2: Run red then convert direct throws**

Run: `cd backend; .\gradlew.bat test --tests com.sweet.market.product.domain.ProductTest --tests com.sweet.market.inventory.domain.InventoryTest`

Expected before implementation: missing enum compilation errors. Replace every direct standard throw in the two aggregates with `new DomainException` using the semantically matching enum; preserve existing idempotent returns.

- [ ] **Step 3: Verify and commit**

Run:

```powershell
cd backend
.\gradlew.bat test --tests com.sweet.market.product.domain.ProductTest --tests com.sweet.market.inventory.domain.InventoryTest
rg -n 'throw new (IllegalArgumentException|IllegalStateException)' src/main/java/com/sweet/market/product/domain/Product.java src/main/java/com/sweet/market/inventory/domain/Inventory.java
```

Expected: tests pass and `rg` has no output.

```powershell
git add backend/src/main/java/com/sweet/market/product/domain backend/src/main/java/com/sweet/market/inventory/domain backend/src/test/java/com/sweet/market/product/domain/ProductTest.java backend/src/test/java/com/sweet/market/inventory/domain/InventoryTest.java
git commit -m "refactor: type product and inventory domain errors"
```

### Task 3: Map product and inventory codes by use case

**Files:**
- Modify: `backend/src/main/java/com/sweet/market/product/application/ProductService.java`
- Modify: `backend/src/main/java/com/sweet/market/product/admin/AdminProductService.java`
- Modify: `backend/src/main/java/com/sweet/market/inventory/application/InventoryService.java`
- Modify: `backend/src/main/java/com/sweet/market/inventory/application/InventoryAdjustmentTransactionService.java`
- Modify: `backend/src/main/java/com/sweet/market/order/application/OrderService.java`
- Modify: `backend/src/main/java/com/sweet/market/cart/application/CartService.java`
- Modify: `backend/src/main/java/com/sweet/market/store/operations/StoreCatalogCommandService.java`
- Test: `backend/src/test/java/com/sweet/market/product/ProductApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/inventory/application/InventoryServiceTransactionTest.java`
- Test: `backend/src/test/java/com/sweet/market/order/OrderApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/store/StoreOperationsApiTest.java`

- [ ] **Step 1: Add API regressions before mapper changes**

Assert existing codes: image required `PRODUCT_IMAGE_REQUIRED`, image limit `PRODUCT_IMAGE_LIMIT_EXCEEDED`, invalid arrangement `VALIDATION_ERROR`, seller change denial `PRODUCT_CHANGE_NOT_ALLOWED`, unavailable order product `PRODUCT_NOT_ON_SALE`, checkout race `CART_CHECKOUT_NOT_ALLOWED`, unavailable inventory `PRODUCT_NOT_ON_SALE`, invalid adjustment `VALIDATION_ERROR`, below-reservation adjustment `INVENTORY_ADJUSTMENT_CONFLICT`.

- [ ] **Step 2: Run the existing API baseline**

Run: `cd backend; .\gradlew.bat test --tests com.sweet.market.product.ProductApiTest --tests com.sweet.market.inventory.application.InventoryServiceTransactionTest --tests com.sweet.market.order.OrderApiTest --tests com.sweet.market.store.StoreOperationsApiTest`

Expected: pass before mapper edits.

- [ ] **Step 3: Replace catches and delete string translation**

Delete `mapProductImageException(IllegalArgumentException)`. Catch `DomainException` and map `IMAGE_REQUIRED`, `IMAGE_LIMIT_EXCEEDED`, `IMAGE_NOT_FOUND`, state codes, and remaining validation codes to their existing `ErrorCode` values. Map inventory stock unavailability to `PRODUCT_NOT_ON_SALE`, below-reservation to `INVENTORY_ADJUSTMENT_CONFLICT`, and public validation to `VALIDATION_ERROR`.

Use this conversion everywhere:

```java
throw new BusinessException(ErrorCode.PRODUCT_IMAGE_REQUIRED, exception);
```

Map `ProductDomainError.NOT_ON_SALE` to `PRODUCT_NOT_ON_SALE` in order creation and `CART_CHECKOUT_NOT_ALLOWED` in checkout. Map catalog product-state errors to `PRODUCT_CHANGE_NOT_ALLOWED`.

- [ ] **Step 4: Verify and commit**

Run the Step 2 command, then run `rg -n 'getMessage\(\)' backend/src/main/java/com/sweet/market/product`. Expected: all tests pass; no result.

```powershell
git add backend/src/main/java/com/sweet/market/product backend/src/main/java/com/sweet/market/inventory backend/src/main/java/com/sweet/market/order/application/OrderService.java backend/src/main/java/com/sweet/market/cart/application/CartService.java backend/src/main/java/com/sweet/market/store/operations/StoreCatalogCommandService.java backend/src/test/java/com/sweet/market
git commit -m "refactor: map product and inventory domain errors"
```

### Task 4: Convert order, payment, and delivery state errors

**Files:**
- Create: `backend/src/main/java/com/sweet/market/order/domain/OrderDomainError.java`
- Create: `backend/src/main/java/com/sweet/market/payment/domain/PaymentDomainError.java`
- Create: `backend/src/main/java/com/sweet/market/delivery/domain/DeliveryDomainError.java`
- Modify: `backend/src/main/java/com/sweet/market/order/domain/Order.java`
- Modify: `backend/src/main/java/com/sweet/market/payment/domain/Payment.java`
- Modify: `backend/src/main/java/com/sweet/market/delivery/domain/Delivery.java`
- Modify: `backend/src/main/java/com/sweet/market/order/application/OrderService.java`
- Modify: `backend/src/main/java/com/sweet/market/payment/application/PaymentService.java`
- Modify: `backend/src/main/java/com/sweet/market/delivery/application/DeliveryService.java`
- Modify: `backend/src/main/java/com/sweet/market/order/application/OrderAutoConfirmService.java`
- Test: `backend/src/test/java/com/sweet/market/order/domain/OrderTest.java`
- Test: `backend/src/test/java/com/sweet/market/payment/domain/PaymentTest.java`
- Test: `backend/src/test/java/com/sweet/market/delivery/domain/DeliveryTest.java`
- Test: `backend/src/test/java/com/sweet/market/order/OrderApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/order/OrderConfirmApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/payment/PaymentApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/delivery/DeliveryApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/order/OrderAutoConfirmServiceTest.java`

- [ ] **Step 1: Change domain tests to typed errors**

```java
enum OrderDomainError implements DomainError {
    PRODUCT_NOT_PURCHASABLE, CANCELLATION_NOT_ALLOWED, PAYMENT_NOT_ALLOWED,
    PAID_ORDER_CANCELLATION_NOT_ALLOWED, SHIPPING_NOT_ALLOWED,
    DELIVERY_COMPLETION_NOT_ALLOWED, REFUND_REQUEST_NOT_ALLOWED,
    REFUND_NOT_ALLOWED, REFUND_REJECTION_NOT_ALLOWED, CONFIRMATION_NOT_ALLOWED
}
enum PaymentDomainError implements DomainError { CANCELLATION_NOT_ALLOWED, REFUND_NOT_ALLOWED }
enum DeliveryDomainError implements DomainError { COMPLETION_NOT_ALLOWED }
```

Use `DomainException#error()` in `OrderTest`, `PaymentTest`, and `DeliveryTest`, then run their three Gradle test selectors. Expected: missing enum compilation failures.

- [ ] **Step 2: Implement typed throws and use-case mappings**

Replace all direct state throws in the three aggregates. Replace application catches with `DomainException` and preserve: order cancel `ORDER_CANCEL_NOT_ALLOWED`, order confirm `ORDER_CONFIRM_NOT_ALLOWED`, payment approve `PAYMENT_APPROVE_NOT_ALLOWED`, payment cancel `PAYMENT_CANCEL_NOT_ALLOWED`, delivery start `DELIVERY_START_NOT_ALLOWED`, delivery complete `DELIVERY_COMPLETE_NOT_ALLOWED`. `OrderAutoConfirmService` catches only `DomainException` and retains its skip behavior.

- [ ] **Step 3: Run API regression suite and commit**

Run: `cd backend; .\gradlew.bat test --tests com.sweet.market.order.OrderApiTest --tests com.sweet.market.order.OrderConfirmApiTest --tests com.sweet.market.payment.PaymentApiTest --tests com.sweet.market.delivery.DeliveryApiTest --tests com.sweet.market.order.OrderAutoConfirmServiceTest`

Expected: all current status and `$.code` contracts pass.

```powershell
git add backend/src/main/java/com/sweet/market/order backend/src/main/java/com/sweet/market/payment backend/src/main/java/com/sweet/market/delivery backend/src/test/java/com/sweet/market/order backend/src/test/java/com/sweet/market/payment backend/src/test/java/com/sweet/market/delivery
git commit -m "refactor: type transaction domain errors"
```

### Task 5: Convert refund, settlement, store, and membership rules

**Files:**
- Create: `backend/src/main/java/com/sweet/market/refund/domain/RefundRequestDomainError.java`
- Create: `backend/src/main/java/com/sweet/market/settlement/domain/SettlementDomainError.java`
- Create: `backend/src/main/java/com/sweet/market/store/domain/StoreDomainError.java`
- Create: `backend/src/main/java/com/sweet/market/store/domain/StoreMembershipDomainError.java`
- Modify: `backend/src/main/java/com/sweet/market/refund/domain/RefundRequest.java`
- Modify: `backend/src/main/java/com/sweet/market/settlement/domain/Settlement.java`
- Modify: `backend/src/main/java/com/sweet/market/store/domain/Store.java`
- Modify: `backend/src/main/java/com/sweet/market/store/domain/StoreMembership.java`
- Modify: `backend/src/main/java/com/sweet/market/refund/application/RefundRequestService.java`
- Modify: `backend/src/main/java/com/sweet/market/settlement/application/SettlementService.java`
- Modify: `backend/src/main/java/com/sweet/market/settlement/batch/SettlementItemProcessor.java`
- Modify: `backend/src/main/java/com/sweet/market/store/application/StoreGovernanceService.java`
- Test: `backend/src/test/java/com/sweet/market/refund/domain/RefundRequestTest.java`
- Test: `backend/src/test/java/com/sweet/market/settlement/domain/SettlementTest.java`
- Test: `backend/src/test/java/com/sweet/market/store/domain/StoreTest.java`
- Test: `backend/src/test/java/com/sweet/market/store/domain/StoreMembershipTest.java`
- Test: `backend/src/test/java/com/sweet/market/refund/RefundRequestApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/settlement/SettlementApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/settlement/batch/SettlementBatchJobTest.java`
- Test: `backend/src/test/java/com/sweet/market/store/StoreApiTest.java`
- Test: `backend/src/test/java/com/sweet/market/store/StoreOperationsApiTest.java`

- [ ] **Step 1: Convert direct domain tests to these exact enums**

```java
enum RefundRequestDomainError implements DomainError {
    HANDLING_NOT_ALLOWED, ORDER_REQUIRED, BUYER_REQUIRED, BUYER_ORDER_MISMATCH,
    REQUEST_REASON_REQUIRED, REQUEST_REASON_LENGTH_INVALID,
    REJECT_REASON_REQUIRED, REJECT_REASON_LENGTH_INVALID
}
enum SettlementDomainError implements DomainError { ORDER_NOT_CONFIRMED }
enum StoreDomainError implements DomainError {
    STATUS_TRANSITION_NOT_ALLOWED, REJECTION_REASON_REQUIRED,
    BUSINESS_RESUBMISSION_NOT_ALLOWED, BUSINESS_INFORMATION_UNAVAILABLE,
    LEGAL_INFORMATION_CHANGE_NOT_ALLOWED
}
enum StoreMembershipDomainError implements DomainError { OWNER_MEMBERSHIP_MISMATCH }
```

Replace message comparisons in `RefundRequestTest`, `SettlementTest`, `StoreTest`, and `StoreMembershipTest` with `DomainException#error()`. Run those selectors; expected: red compilation failures.

- [ ] **Step 2: Implement typed throws and map public use cases**

Replace every direct standard throw in `RefundRequest`, `Settlement`, `Store`, and `StoreMembership`. Map refund creation errors to `REFUND_REQUEST_NOT_ALLOWED`, refund handling errors to `REFUND_REQUEST_HANDLE_NOT_ALLOWED`, settlement error to `SETTLEMENT_CREATE_NOT_ALLOWED`, and store rejection reason to `VALIDATION_ERROR` with other store errors mapped to `STORE_CHANGE_NOT_ALLOWED`. `SettlementItemProcessor` catches `DomainException` before wrapping it in its existing skippable exception.

- [ ] **Step 3: Verify focused API and batch regressions, then commit**

Run: `cd backend; .\gradlew.bat test --tests com.sweet.market.refund.RefundRequestApiTest --tests com.sweet.market.settlement.SettlementApiTest --tests com.sweet.market.settlement.batch.SettlementBatchJobTest --tests com.sweet.market.store.StoreApiTest --tests com.sweet.market.store.StoreOperationsApiTest`

Expected: existing API codes and batch skip counts pass.

```powershell
git add backend/src/main/java/com/sweet/market/refund backend/src/main/java/com/sweet/market/settlement backend/src/main/java/com/sweet/market/store backend/src/test/java/com/sweet/market/refund backend/src/test/java/com/sweet/market/settlement backend/src/test/java/com/sweet/market/store
git commit -m "refactor: type remaining domain errors"
```

### Task 6: Exhaustively verify backend and web compatibility

**Files:**
- No planned production edits; never modify web source for this refactor.

- [ ] **Step 1: Audit all direct domain exceptions and boundary catches**

Run:

```powershell
cd backend
rg -n 'throw new (IllegalArgumentException|IllegalStateException)' src/main/java/com/sweet/market -g '*/domain/*.java'
rg -n 'catch \(IllegalArgumentException|catch \(IllegalStateException|catch \(IllegalArgumentException \| IllegalStateException' src/main/java/com/sweet/market
```

Expected: no target-domain direct throws; remaining catches are only out-of-scope JWT or third-party parsing. A target-domain catch match is a failed verification and must be resolved in the task that owns that use case before continuing.

- [ ] **Step 2: Run complete backend tests**

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build web without changing it**

Run: `cd web; npm run build`

Expected: build succeeds.

- [ ] **Step 4: Review the final diff**

Run `git diff --check`; expected: no whitespace errors.

Run `git status --short` and confirm that the only unrelated unstaged change is the pre-existing handoff document.
