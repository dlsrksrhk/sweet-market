# Milestone 29 Concurrent Purchase And Inventory Reservation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** Make direct orders and cart checkout idempotent and concurrency-safe so single-item and stock-managed products are never oversold.

**Architecture:** A purchase application boundary claims a buyer-scoped idempotency request, revalidates current price and eligibility, then reserves with conditional PostgreSQL updates. Direct orders and cart checkout delegate to that boundary; cart checkout processes products by ascending ID and remains all-or-nothing. Existing cancellation, payment compensation, coupon, and unpaid-order-expiry paths remain the release authority.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL/Testcontainers, Flyway, React, TypeScript, TanStack Query, Vite.

## Global Constraints

- Backend commands use JDK 21 and JWT_SECRET=sweet-market-local-test-secret-key-32bytes-minimum.
- New JUnit test method names are Korean and use underscores.
- Direct order and cart checkout require Idempotency-Key. The unique database key is (buyer_id, idempotency_key).
- The same key and fingerprint replay the initial success or business-failure result for 48 hours. A changed fingerprint returns 409 IDEMPOTENCY_KEY_REUSED; an active matching request returns 409 ORDER_REQUEST_IN_PROGRESS.
- PROCESSING records use a five-minute lease plus execution UUID. Only a locked, expired same-fingerprint row can be reclaimed.
- SINGLE_ITEM reserves with ON_SALE to RESERVED conditional update. STOCK_MANAGED reserves with a conditional available-quantity increment.
- Cart checkout has no coupon selection, processes product IDs in ascending order, and rolls back every new order/reservation if any item fails.
- Cart failure payloads name the blocking item and reason but never reveal quantity.
- Never invoke an external payment gateway while reservation database work is open.
- Preserve M28 pricing order, coupon reservation, zero-price approval, cancellation, failure compensation, and expiry behavior.

---

## File Structure

| Path | Responsibility |
| --- | --- |
| backend/src/main/resources/db/migration/V13__add_purchase_requests.sql | Durable idempotency record and indexes. |
| backend/src/main/java/com/sweet/market/purchase/domain/PurchaseRequest.java | State, fingerprint, lease, execution UUID, terminal result, and 48-hour retention. |
| backend/src/main/java/com/sweet/market/purchase/repository/PurchaseRequestRepository.java | Normal and locked key lookup. |
| backend/src/main/java/com/sweet/market/purchase/application/PurchaseRequestService.java | Claim, replay, completion, lease recovery, cleanup. |
| backend/src/main/java/com/sweet/market/purchase/scheduler/PurchaseRequestCleanupScheduler.java | Daily deletion of terminal requests whose 48-hour retention elapsed. |
| backend/src/main/java/com/sweet/market/purchase/application/PurchaseReservationService.java | Shared direct/cart transaction boundary. |
| backend/src/main/java/com/sweet/market/purchase/application/ProductReservationService.java | Conditional product reservation and audit snapshot creation. |
| backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java | Conditional single-item update. |
| backend/src/main/java/com/sweet/market/inventory/repository/InventoryRepository.java | Conditional stock increment. |
| backend/src/main/java/com/sweet/market/order/application/OrderService.java | Direct-order adapter to purchase boundary. |
| backend/src/main/java/com/sweet/market/cart/application/CartService.java | Cart-checkout adapter to purchase boundary. |
| backend/src/main/java/com/sweet/market/purchase/api/CartCheckoutFailure.java | Replayable item-level cart failure contract. |
| backend/src/test/java/com/sweet/market/purchase | Purchase persistence, replay, release, and race tests. |
| web/src/features/orders/orderApi.ts | Direct purchase key header. |
| web/src/features/cart/cartApi.ts | Cart key header and failure types. |
| web/src/pages/ProductDetailPage.tsx | Per-action direct-order retry key. |
| web/src/pages/MyCartPage.tsx | Cart retry key, retained selection, item failure UI. |
| docs/superpowers/reports/2026-07-16-milestone-29-locking-comparison.md | PostgreSQL locking experiment. |

### Task 1: Persist and claim idempotent purchase requests

**Files:**
- Create: backend/src/main/resources/db/migration/V13__add_purchase_requests.sql
- Create: backend/src/main/java/com/sweet/market/purchase/domain/PurchaseRequest.java
- Create: backend/src/main/java/com/sweet/market/purchase/domain/PurchaseRequestStatus.java
- Create: backend/src/main/java/com/sweet/market/purchase/repository/PurchaseRequestRepository.java
- Create: backend/src/main/java/com/sweet/market/purchase/application/PurchaseRequestService.java
- Create: backend/src/main/java/com/sweet/market/purchase/scheduler/PurchaseRequestCleanupScheduler.java
- Modify: backend/src/main/java/com/sweet/market/common/error/ErrorCode.java
- Test: backend/src/test/java/com/sweet/market/purchase/PurchaseRequestServiceTest.java

**Interfaces:**
- Produces PurchaseRequestService.claim(Long buyerId, String key, String fingerprint, Instant now).
- Claim variants are New(UUID executionToken), Processing, and Replay(int httpStatus, JsonNode payload).
- Produces completeSuccess and completeBusinessFailure, both requiring the current execution token.

- [ ] **Step 1: Write failing persistence and replay tests**

~~~java
@Test
void 동일한_키와_요청은_완료_응답을_재사용한다() {
    Claim.New claim = (Claim.New) service.claim(buyerId, "key-1", "direct:10:", now);
    service.completeSuccess(buyerId, "key-1", claim.executionToken(), 201, payload, now);

    Claim.Replay replay = (Claim.Replay) service.claim(buyerId, "key-1", "direct:10:", now.plusSeconds(1));

    assertThat(replay.httpStatus()).isEqualTo(201);
    assertThat(replay.payload()).isEqualTo(payload);
}

@Test
void 다른_요청으로_같은_키를_재사용하면_거부한다() {
    service.claim(buyerId, "key-1", "direct:10:", now);

    assertThatThrownBy(() -> service.claim(buyerId, "key-1", "direct:11:", now))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.IDEMPOTENCY_KEY_REUSED);
}
~~~

- [ ] **Step 2: Run the focused test**

~~~powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.purchase.PurchaseRequestServiceTest' --rerun-tasks
~~~

Expected: compilation failure because purchase request types do not exist.

- [ ] **Step 3: Implement migration and claim service**

~~~sql
CREATE TABLE purchase_requests (
    id BIGSERIAL PRIMARY KEY,
    buyer_id BIGINT NOT NULL REFERENCES members(id),
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    execution_token UUID NOT NULL,
    lease_expires_at TIMESTAMPTZ NOT NULL,
    response_status INTEGER,
    response_payload JSONB,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_purchase_requests_buyer_key UNIQUE (buyer_id, idempotency_key),
    CONSTRAINT chk_purchase_requests_status CHECK (status IN ('PROCESSING', 'COMPLETED'))
);
CREATE INDEX idx_purchase_requests_expiry ON purchase_requests (expires_at);
CREATE INDEX idx_purchase_requests_processing_lease
    ON purchase_requests (lease_expires_at) WHERE status = 'PROCESSING';
~~~

Implement a locked repository query:

~~~java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("""
        select request
        from PurchaseRequest request
        where request.buyer.id = :buyerId
          and request.idempotencyKey = :key
        """)
Optional<PurchaseRequest> findForUpdate(@Param("buyerId") Long buyerId, @Param("key") String key);
~~~

Claim in a REQUIRES_NEW transaction. A new row gets now.plus(Duration.ofMinutes(5)) lease. Existing completed matching fingerprints replay stored JSON. Existing matching processing rows throw ORDER_REQUEST_IN_PROGRESS unless the lease is expired, in which case rotate the UUID under lock. Completion requires a matching UUID, writes status/payload, and sets expiresAt to now.plus(Duration.ofHours(48)). Add deleteByStatusAndExpiresAtBefore(PurchaseRequestStatus status, Instant cutoff) to the repository and a scheduler that calls purgeCompletedBefore(Instant.now()) once daily; it deletes only COMPLETED rows. Add ORDER_REQUEST_IN_PROGRESS, IDEMPOTENCY_KEY_REUSED, PRODUCT_SOLD_OUT, and PRODUCT_UNAVAILABLE error codes with HTTP 409.

- [ ] **Step 4: Run focused tests**

Run the Step 2 command. Expected: BUILD SUCCESSFUL; replay, processing conflict, and fingerprint conflict pass.

- [ ] **Step 5: Commit**

~~~powershell
git add backend/src/main/resources/db/migration/V13__add_purchase_requests.sql backend/src/main/java/com/sweet/market/purchase backend/src/main/java/com/sweet/market/common/error/ErrorCode.java backend/src/test/java/com/sweet/market/purchase/PurchaseRequestServiceTest.java
git commit -m "feat: persist idempotent purchase requests"
~~~

### Task 2: Reserve products with conditional updates

**Files:**
- Modify: backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java
- Modify: backend/src/main/java/com/sweet/market/inventory/repository/InventoryRepository.java
- Modify: backend/src/main/java/com/sweet/market/inventory/domain/InventoryAdjustment.java
- Create: backend/src/main/java/com/sweet/market/purchase/application/ProductReservationService.java
- Test: backend/src/test/java/com/sweet/market/purchase/ProductReservationServiceTest.java

**Interfaces:**
- Produces ProductReservationService.reserve(Order order).
- Produces ProductRepository.reserveSingleItemIfOnSale(Long productId) and InventoryRepository.reserveOneIfAvailable(Long productId), both returning affected-row count.
- Stock success creates exactly one RESERVATION adjustment using refreshed snapshot values.

- [ ] **Step 1: Write failing conditional-reservation tests**

~~~java
@Test
void 재고형_상품은_가용수량이_있을때만_한개를_예약한다() {
    Order order = createPersistedStockOrder(1);

    reservationService.reserve(order);

    assertThat(availableQuantity(order.getProduct().getId())).isZero();
    assertThat(countInventoryAdjustments(order.getId(), "RESERVATION")).isOne();
}

@Test
void 단품은_판매중일때_한번만_예약한다() {
    Order winner = createPersistedSingleItemOrder();
    Order loser = createPersistedSingleItemOrderForAnotherBuyer(winner.getProduct());

    reservationService.reserve(winner);

    assertThatThrownBy(() -> reservationService.reserve(loser))
            .isInstanceOf(BusinessException.class)
            .extracting(error -> ((BusinessException) error).errorCode())
            .isEqualTo(ErrorCode.PRODUCT_SOLD_OUT);
}
~~~

- [ ] **Step 2: Run the test**

~~~powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.purchase.ProductReservationServiceTest' --rerun-tasks
~~~

Expected: compilation failure for ProductReservationService.

- [ ] **Step 3: Implement the conditional repository methods and snapshot factory**

~~~java
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("""
        update Product product
        set product.status = com.sweet.market.product.domain.ProductStatus.RESERVED
        where product.id = :productId
          and product.status = com.sweet.market.product.domain.ProductStatus.ON_SALE
        """)
int reserveSingleItemIfOnSale(@Param("productId") Long productId);

@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query(value = """
        update inventories
        set reserved_quantity = reserved_quantity + 1,
            version = version + 1
        where product_id = :productId
          and total_quantity - reserved_quantity > 0
        """, nativeQuery = true)
int reserveOneIfAvailable(@Param("productId") Long productId);
~~~

For stock, update first and reload Inventory in the same transaction. The update holds its row lock through commit, so afterReservedQuantity is stable and beforeReservedQuantity is afterReservedQuantity minus one. Add InventoryAdjustment.reservation(Inventory, Order, int beforeReservedQuantity, int afterReservedQuantity). A zero row count maps to PRODUCT_SOLD_OUT if the product remains purchasable and PRODUCT_UNAVAILABLE otherwise. Do not rely on a stale managed Inventory after the bulk update.

- [ ] **Step 4: Run focused regression tests**

~~~powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.purchase.ProductReservationServiceTest' --tests 'com.sweet.market.cart.CartCheckoutApiTest' --rerun-tasks
~~~

Expected: BUILD SUCCESSFUL and failed updates create no adjustment.

- [ ] **Step 5: Commit**

~~~powershell
git add backend/src/main/java/com/sweet/market/product/repository/ProductRepository.java backend/src/main/java/com/sweet/market/inventory/repository/InventoryRepository.java backend/src/main/java/com/sweet/market/inventory/domain/InventoryAdjustment.java backend/src/main/java/com/sweet/market/purchase/application/ProductReservationService.java backend/src/test/java/com/sweet/market/purchase/ProductReservationServiceTest.java
git commit -m "feat: reserve products with conditional updates"
~~~

### Task 3: Route direct orders through the shared purchase boundary

**Files:**
- Create: backend/src/main/java/com/sweet/market/purchase/application/DirectPurchaseCommand.java
- Create: backend/src/main/java/com/sweet/market/purchase/application/PurchaseReservationService.java
- Modify: backend/src/main/java/com/sweet/market/order/domain/Order.java
- Modify: backend/src/main/java/com/sweet/market/order/application/OrderService.java
- Modify: backend/src/main/java/com/sweet/market/order/api/OrderController.java
- Test: backend/src/test/java/com/sweet/market/order/OrderApiTest.java
- Test: backend/src/test/java/com/sweet/market/purchase/DirectPurchaseConcurrencyTest.java

**Interfaces:**
- Produces purchaseDirect(DirectPurchaseCommand command, String idempotencyKey).
- Direct command contains buyerId, productId, and nullable memberCouponId.
- POST /api/orders requires Idempotency-Key and returns 201 for initial and replayed success.

- [ ] **Step 1: Write failing API and concurrency tests**

~~~java
@Test
void 동일한_직접주문_키는_주문을_한번만_생성한다() throws Exception {
    String first = createOrder(buyerToken, productId, null, "direct-retry-1")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    String second = createOrder(buyerToken, productId, null, "direct-retry-1")
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();

    assertThat(orderId(first)).isEqualTo(orderId(second));
    assertThat(countOrders()).isEqualTo(1);
}

@Test
void 하나의_단품에_동시구매자는_한명만_성공한다() {
    List<Integer> statuses = invokeTogether(buyers.stream()
            .map(token -> () -> postDirectOrderAfterBarrier(token, productId, UUID.randomUUID().toString()).getStatus())
            .toList());

    assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
    assertThat(statuses).filteredOn(status -> status == 409).hasSize(buyers.size() - 1);
}
~~~

- [ ] **Step 2: Run focused tests**

~~~powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.order.OrderApiTest' --tests 'com.sweet.market.purchase.DirectPurchaseConcurrencyTest' --rerun-tasks
~~~

Expected: failure because the current endpoint accepts duplicate creation and has no header contract.

- [ ] **Step 3: Implement direct purchase**

~~~java
public OrderResponse purchaseDirect(DirectPurchaseCommand command, String key) {
    String fingerprint = "direct:%d:%s".formatted(command.productId(), Objects.toString(command.memberCouponId(), ""));
    Claim claim = requestService.claim(command.buyerId(), key, fingerprint, Instant.now());
    if (claim instanceof Claim.Replay replay) {
        return objectMapper.convertValue(replay.payload().path("data"), OrderResponse.class);
    }
    UUID token = ((Claim.New) claim).executionToken();
    try {
        OrderResponse response = transactionTemplate.execute(status -> reserveDirect(command));
        requestService.completeSuccess(command.buyerId(), key, token, 201, objectMapper.valueToTree(ApiResponse.ok(response)), Instant.now());
        return response;
    } catch (BusinessException exception) {
        requestService.completeBusinessFailure(command.buyerId(), key, token, exception.errorCode(), Instant.now());
        throw exception;
    }
}
~~~

reserveDirect reloads buyer/product, quotes promotion, revalidates an optional coupon, saves an Order, conditionally reserves it, then reserves the coupon. Refactor Order.create so it does not call product.reserve(); ProductReservationService is the only reservation state writer. Keep zero-price internal approval after successful reservation. Add @RequestHeader("Idempotency-Key") and reject blank input as VALIDATION_ERROR.

- [ ] **Step 4: Run direct-order, coupon, and payment suites**

~~~powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.order.OrderApiTest' --tests 'com.sweet.market.purchase.DirectPurchaseConcurrencyTest' --tests 'com.sweet.market.coupon.CouponRedemptionConcurrencyTest' --tests 'com.sweet.market.payment.PaymentApiTest' --rerun-tasks
~~~

Expected: BUILD SUCCESSFUL; a coupon order that loses product race has no active coupon reservation.

- [ ] **Step 5: Commit**

~~~powershell
git add backend/src/main/java/com/sweet/market/purchase/application backend/src/main/java/com/sweet/market/order/domain/Order.java backend/src/main/java/com/sweet/market/order/application/OrderService.java backend/src/main/java/com/sweet/market/order/api/OrderController.java backend/src/test/java/com/sweet/market/order/OrderApiTest.java backend/src/test/java/com/sweet/market/purchase/DirectPurchaseConcurrencyTest.java
git commit -m "feat: make direct orders idempotent"
~~~

### Task 4: Make cart checkout atomic under contention

**Files:**
- Create: backend/src/main/java/com/sweet/market/purchase/api/CartCheckoutFailure.java
- Create: backend/src/main/java/com/sweet/market/purchase/api/CartCheckoutFailureItem.java
- Create: backend/src/main/java/com/sweet/market/purchase/api/CartCheckoutFailureException.java
- Create: backend/src/main/java/com/sweet/market/purchase/api/CartCheckoutFailureResponse.java
- Create: backend/src/main/java/com/sweet/market/purchase/application/CartPurchaseCommand.java
- Modify: backend/src/main/java/com/sweet/market/purchase/application/PurchaseReservationService.java
- Modify: backend/src/main/java/com/sweet/market/cart/application/CartService.java
- Modify: backend/src/main/java/com/sweet/market/cart/api/CartCheckoutController.java
- Modify: backend/src/main/java/com/sweet/market/cart/api/CartCheckoutResponse.java
- Modify: backend/src/main/java/com/sweet/market/common/error/GlobalExceptionHandler.java
- Test: backend/src/test/java/com/sweet/market/cart/CartCheckoutApiTest.java
- Test: backend/src/test/java/com/sweet/market/purchase/CartPurchaseConcurrencyTest.java

**Interfaces:**
- Produces purchaseCart(CartPurchaseCommand command, String idempotencyKey).
- CartCheckoutFailureItem has cartItemId, productId, productTitle, and reason SOLD_OUT or UNAVAILABLE.
- Same-key cart success and business failure replay the stored payload.
- CartCheckoutFailureException is mapped to an HTTP 409 CartCheckoutFailureResponse with code, message, and data fields.

- [ ] **Step 1: Write failing cart race and replay tests**

~~~java
@Test
void 경쟁으로_장바구니_항목이_품절되면_문제상품을_반환하고_전체를_롤백한다() throws Exception {
    CartCheckoutFailure failure = checkoutAfterOtherBuyerWins(cartItemIds, "cart-race-1")
            .andExpect(status().isConflict()).andReturnFailure(CartCheckoutFailure.class);

    assertThat(failure.items()).extracting(CartCheckoutFailureItem::productId).contains(stockProductId);
    assertThat(countOrders()).isZero();
    assertThat(countCartItemsFor(buyerId)).isEqualTo(2);
    assertInventory(availableProductId, 1, 0);
}

@Test
void 같은_장바구니_키는_성공결과를_재사용한다() throws Exception {
    CartCheckoutResponse first = checkout(cartItemIds, "cart-retry-1").andReturnResponse();
    CartCheckoutResponse second = checkout(cartItemIds, "cart-retry-1").andReturnResponse();

    assertThat(second.orders()).extracting(OrderSummaryResponse::id)
            .containsExactlyElementsOf(first.orders().stream().map(OrderSummaryResponse::id).toList());
}
~~~

- [ ] **Step 2: Run focused cart tests**

~~~powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.cart.CartCheckoutApiTest' --tests 'com.sweet.market.purchase.CartPurchaseConcurrencyTest' --rerun-tasks
~~~

Expected: failure because cart checkout has no header, replay, or item-specific race response.

- [ ] **Step 3: Implement ascending, all-or-nothing cart purchase**

~~~java
List<CartItem> items = cartItemRepository.findAllWithBuyerProductSellerImagesByIdIn(command.cartItemIds());
validateCartItems(command.buyerId(), command.cartItemIds(), items);

List<CartItem> ordered = items.stream()
        .sorted(comparing(item -> item.getProduct().getId()))
        .toList();
List<Order> orders = new ArrayList<>();
for (CartItem item : ordered) {
    PromotionPrice price = promotionPricingService.quote(item.getProduct());
    Order order = orderRepository.save(Order.create(item.getBuyer(), item.getProduct(), price));
    productReservationService.reserve(order);
    orders.add(order);
}
cartItemRepository.deleteAll(items);
return new CartCheckoutResponse(orders.stream().map(OrderSummaryResponse::from).toList());
~~~

Run the loop inside the purchase request transaction. Convert a PRODUCT_SOLD_OUT or PRODUCT_UNAVAILABLE from its item into CartCheckoutFailure, persist that payload as the purchase request terminal failure, and throw CartCheckoutFailureException so the transaction rolls back preceding orders, conditional updates, adjustment rows, and cart deletion. Add a dedicated GlobalExceptionHandler method returning ResponseEntity.status(HttpStatus.CONFLICT).body(new CartCheckoutFailureResponse("CART_CHECKOUT_NOT_ALLOWED", "주문할 수 없는 장바구니 항목이 포함되어 있습니다.", failure)). On same-key replay, deserialize the stored failure into that exception. Add required Idempotency-Key header forwarding. Cart checkout creates no coupon reservation.

- [ ] **Step 4: Run cart compatibility suite**

~~~powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.cart.CartCheckoutApiTest' --tests 'com.sweet.market.cart.CartApiTest' --tests 'com.sweet.market.purchase.CartPurchaseConcurrencyTest' --tests 'com.sweet.market.order.OrderApiTest' --rerun-tasks
~~~

Expected: BUILD SUCCESSFUL; a failed cart keeps every selected row and restores all preceding reservation changes.

- [ ] **Step 5: Commit**

~~~powershell
git add backend/src/main/java/com/sweet/market/purchase/api backend/src/main/java/com/sweet/market/purchase/application backend/src/main/java/com/sweet/market/cart/application/CartService.java backend/src/main/java/com/sweet/market/cart/api backend/src/test/java/com/sweet/market/cart/CartCheckoutApiTest.java backend/src/test/java/com/sweet/market/purchase/CartPurchaseConcurrencyTest.java
git commit -m "feat: reserve cart orders atomically"
~~~

### Task 5: Add buyer retry keys and cart failure rendering

**Files:**
- Modify: web/src/features/orders/orderApi.ts
- Modify: web/src/features/cart/cartApi.ts
- Modify: web/src/pages/ProductDetailPage.tsx
- Modify: web/src/pages/MyCartPage.tsx
- Verify without source change: web/src/features/stores/StoreCatalogPanel.tsx

**Interfaces:**
- Produces createOrder(productId, memberCouponId, idempotencyKey) and checkoutCart(cartItemIds, idempotencyKey).
- Produces TypeScript CartCheckoutFailureItem matching backend fields.
- StoreCatalogPanel remains unchanged because it already renders total, reserved, and available quantities.

- [ ] **Step 1: Change client signatures so callers fail until they pass a key**

~~~ts
export function createOrder(productId: number, memberCouponId: number | null, idempotencyKey: string) {
  return api<Order>('/api/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ productId, memberCouponId }),
  });
}

export type CartCheckoutFailureItem = {
  cartItemId: number;
  productId: number;
  productTitle: string;
  reason: 'SOLD_OUT' | 'UNAVAILABLE';
};
~~~

- [ ] **Step 2: Run web build**

~~~powershell
cd web
npm run build
~~~

Expected: TypeScript errors because ProductDetailPage and MyCartPage still call APIs without a key.

- [ ] **Step 3: Implement stable per-action retry state**

Create a key with crypto.randomUUID only when a buyer explicitly starts an order or checkout. Keep the key in state for a retryable 409 ORDER_REQUEST_IN_PROGRESS; clear it after success or a terminal business failure. Parse CartCheckoutFailure from ApiError data, keep selected cart IDs unchanged, and render this per affected row:

~~~tsx
{checkoutFailureItems.get(item.cartItemId) ? (
  <span className="error-text">
    {checkoutFailureItems.get(item.cartItemId)?.reason === 'SOLD_OUT'
      ? '방금 품절되었습니다.'
      : '현재 구매할 수 없습니다.'}
  </span>
) : null}
~~~

- [ ] **Step 4: Build and manually verify**

~~~powershell
cd web
npm run build
npm run dev
~~~

Expected: build succeeds. Browser verification: direct and cart requests include Idempotency-Key; cart race preserves selection and shows item reason; StoreCatalogPanel still displays total, reserved, and available.

- [ ] **Step 5: Commit**

~~~powershell
git add web/src/features/orders/orderApi.ts web/src/features/cart/cartApi.ts web/src/pages/ProductDetailPage.tsx web/src/pages/MyCartPage.tsx
git commit -m "feat: retry purchases with idempotency keys"
~~~

### Task 6: Measure races and verify release/coupon recovery

**Files:**
- Create: backend/src/test/java/com/sweet/market/purchase/PurchaseReservationConcurrencyTest.java
- Create: backend/src/test/java/com/sweet/market/purchase/InventoryLockingComparisonTest.java
- Modify: backend/src/test/java/com/sweet/market/payment/PaymentApiTest.java
- Modify: backend/src/test/java/com/sweet/market/coupon/CouponRedemptionConcurrencyTest.java
- Create: docs/superpowers/reports/2026-07-16-milestone-29-locking-comparison.md

**Interfaces:**
- Produces barrier-based PostgreSQL scenarios for conditional update, bounded optimistic retry, and pessimistic lock.
- Production code uses conditional update only.

- [ ] **Step 1: Write failing race and recovery tests**

~~~java
@Test
void 재고보다_많은_동시구매에서_성공수는_가용수량과_같다() {
    PurchaseAttemptResult result = runConcurrentPurchases(createStockProduct(3), 10);

    assertThat(result.successCount()).isEqualTo(3);
    assertThat(result.conflictCount()).isEqualTo(7);
    assertThat(availableQuantity(result.productId())).isZero();
}

@Test
void 실패한_결제와_중복취소는_예약재고를_한번만_복구한다() {
    Long orderId = createReservedStockOrder();

    failPayment(orderId);
    cancelAgainIfAllowed(orderId);

    assertInventory(productId, 1, 0);
    assertThat(countInventoryAdjustments(orderId, "RELEASE")).isEqualTo(1);
}
~~~

- [ ] **Step 2: Run the concurrency tests**

~~~powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.purchase.PurchaseReservationConcurrencyTest' --tests 'com.sweet.market.purchase.InventoryLockingComparisonTest' --rerun-tasks
~~~

Expected: failures until all shared reservation and recovery paths are integrated.

- [ ] **Step 3: Implement the repeatable comparison harness**

~~~java
record ExperimentResult(String strategy, int successes, int conflicts, int retries, Duration elapsed) {}

ExperimentResult runConditionalUpdateScenario(int buyers, int stock);
ExperimentResult runOptimisticRetryScenario(int buyers, int stock, int maxRetries);
ExperimentResult runPessimisticLockScenario(int buyers, int stock);
~~~

Use the same fixture stock, buyer count, CountDownLatch barrier, and timing method for all three. Assert each scenario has successes equal to stock, conflicts equal to buyers minus stock, and no negative available quantity. Record retries only for optimistic mode. Capture elapsed time using System.nanoTime. The report records the exact command, PostgreSQL/Testcontainers version, fixture, warm-up policy, three result rows, lock/conflict observations, and why conditional SQL remains production baseline.

- [ ] **Step 4: Run focused M29 regression suites**

~~~powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.purchase.*' --tests 'com.sweet.market.order.OrderApiTest' --tests 'com.sweet.market.cart.CartCheckoutApiTest' --tests 'com.sweet.market.payment.PaymentApiTest' --tests 'com.sweet.market.coupon.CouponRedemptionConcurrencyTest' --rerun-tasks
~~~

Expected: BUILD SUCCESSFUL; coupon loser cleanup and exactly-once release pass.

- [ ] **Step 5: Commit tests and report**

~~~powershell
git add backend/src/test/java/com/sweet/market/purchase backend/src/test/java/com/sweet/market/payment/PaymentApiTest.java backend/src/test/java/com/sweet/market/coupon/CouponRedemptionConcurrencyTest.java docs/superpowers/reports/2026-07-16-milestone-29-locking-comparison.md
git commit -m "test: measure concurrent inventory reservation"
~~~

### Task 7: Verify delivery and hand off to M30

**Files:**
- Create: docs/superpowers/handoffs/2026-07-16-milestone-29-concurrent-purchase-and-inventory-reservation-handoff.md
- Create: docs/superpowers/handoffs/2026-07-16-post-milestone-29-next-session-handoff.md

**Interfaces:**
- Produces reproducible evidence and preserves M29 invariants for the M30 session.

- [ ] **Step 1: Run complete backend suite**

~~~powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --rerun-tasks
~~~

Expected: BUILD SUCCESSFUL with zero failed tests.

- [ ] **Step 2: Run web build and diff check**

~~~powershell
cd web
npm run build
cd ..
git diff --check
~~~

Expected: production build succeeds and diff check has no diagnostics.

- [ ] **Step 3: Run manual local QA**

~~~powershell
cd backend
docker compose up -d
~~~

Verify direct replay returns the same order, in-progress duplicate returns 409, stock N has N winners, cart race retains all items with a reason, losing coupon remains unconsumed, cancellation/payment failure restores stock once, and store catalog displays total/reserved/available.

- [ ] **Step 4: Write handoff evidence**

Milestone handoff records migration V13, API/header rules, exact test/build output, locking results, and manual QA. Post-milestone handoff names M30 as next and prohibits weakening conditional reservations or idempotency semantics.

- [ ] **Step 5: Commit handoff documents**

~~~powershell
git add docs/superpowers/handoffs/2026-07-16-milestone-29-concurrent-purchase-and-inventory-reservation-handoff.md docs/superpowers/handoffs/2026-07-16-post-milestone-29-next-session-handoff.md
git commit -m "docs: hand off milestone 29 inventory reservation"
~~~
