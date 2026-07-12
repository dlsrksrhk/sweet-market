# Milestone 23 Product Sales Policy And Stock-Managed Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add immutable product sales policies, stock-managed inventory, audited sequential reservation, and buyer-safe availability views.

**Architecture:** Product owns metadata, policy, and visibility. A one-to-one Inventory aggregate owns stock quantities and its immutable audit history; InventoryService coordinates it with order, payment, delivery, cart, and store-operation services. Public projections use a lightweight inventory join and never load adjustment collections.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL/Flyway, JUnit 5/MockMvc/Testcontainers, React, TypeScript, TanStack Query, React Hook Form, Vite.

## Global Constraints

- Existing products migrate to SINGLE_ITEM and preserve ON_SALE -> RESERVED -> SOLD_OUT behavior.
- Only active BUSINESS stores may create STOCK_MANAGED products.
- Inventory is the sole authority for stock-managed availability; product status remains visibility only.
- Buyer responses expose IN_STOCK, LOW_STOCK, or SOLD_OUT only; total/reserved quantities and adjustment notes are operator-only.
- Active OWNER and MANAGER memberships may adjust inventory and read history. Suspended stores remain read-only.
- Pre-shipping cancellation/payment failure releases a reservation once; shipment commits it; refunds do not auto-restock.
- New JUnit test method names must be Korean with underscores.
- Preserve M22 public/operator query budgets and zero collection fetches.

## File Map

- Create backend/src/main/resources/db/migration/V5__add_product_sales_policy_and_inventory.sql.
- Create backend/src/main/java/com/sweet/market/inventory/{domain,repository,application,api}/**.
- Modify backend/src/main/java/com/sweet/market/product/**, cart/**, order/**, payment/**, delivery/**, store/{operations,storefront}/**.
- Modify web/src/features/{products,stores,cart}/** and web/src/pages/{ProductFormPage.tsx,StoreProfilePage.tsx,MyCartPage.tsx}.
- Add focused tests under backend/src/test/java/com/sweet/market/{inventory,product,cart,order,store,jpalab}/**.

### Task 1: Add schema, policy, and inventory domain invariants

**Files:** Create V5 migration; ProductSalesPolicy.java; Inventory.java; InventoryAdjustment.java; InventoryChangeType.java; InventoryAdjustmentReason.java. Modify Product.java. Test InventoryTest.java and InventoryMigrationTest.java.

**Interfaces:** Inventory.initialize(Product,int); reserve(Order); release(Order); commitShipment(Order); adjust(int,InventoryAdjustmentReason,String,Member). Product.create(Store,String,String,long,ProductSalesPolicy,Integer,Integer), isSingleItem(), and isVisibleForNewOrder().

- [ ] **Step 1: Write failing domain/migration tests**

    @Test void 재고는_예약보다_낮은_총수량으로_조정할_수_없다() {
        Inventory inventory = Inventory.initialize(stockProduct, 3);
        inventory.reserve(order);
        assertThatThrownBy(() -> inventory.adjust(0, STOCKTAKE, null, operator))
                .isInstanceOf(IllegalStateException.class);
    }

- [ ] **Step 2: Run the tests and verify failure**

    cd backend; $env:JAVA_HOME='C:\java\jdk-21'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat test --tests 'com.sweet.market.inventory.domain.InventoryTest' --tests 'com.sweet.market.store.migration.InventoryMigrationTest'

Expected: FAIL because the policy column and Inventory types do not exist.

- [ ] **Step 3: Implement migration and aggregate**

    ALTER TABLE products ADD COLUMN sales_policy VARCHAR(20);
    UPDATE products SET sales_policy = 'SINGLE_ITEM' WHERE sales_policy IS NULL;
    ALTER TABLE products ALTER COLUMN sales_policy SET NOT NULL;
    CREATE TABLE inventories (id BIGSERIAL PRIMARY KEY, product_id BIGINT NOT NULL UNIQUE REFERENCES products(id), total_quantity INTEGER NOT NULL CHECK (total_quantity >= 0), reserved_quantity INTEGER NOT NULL CHECK (reserved_quantity >= 0), version BIGINT NOT NULL);
    CREATE TABLE inventory_adjustments (id BIGSERIAL PRIMARY KEY, inventory_id BIGINT NOT NULL REFERENCES inventories(id), order_id BIGINT REFERENCES orders(id), actor_member_id BIGINT REFERENCES members(id), change_type VARCHAR(30) NOT NULL, reason VARCHAR(30), reference_note VARCHAR(500), before_total_quantity INTEGER NOT NULL, after_total_quantity INTEGER NOT NULL, before_reserved_quantity INTEGER NOT NULL, after_reserved_quantity INTEGER NOT NULL, occurred_at TIMESTAMP NOT NULL);

Implement availableQuantity as totalQuantity - reservedQuantity; reject any mutation that makes total lower than reserved. Create an append-only adjustment for initialization, manual adjustment, reservation, release, and shipment commitment.

- [ ] **Step 4: Re-run focused tests**

    cd backend; .\gradlew.bat test --tests 'com.sweet.market.inventory.domain.InventoryTest' --tests 'com.sweet.market.store.migration.*'

Expected: PASS.

- [ ] **Step 5: Commit**

    git add backend/src/main/resources/db/migration/V5__add_product_sales_policy_and_inventory.sql backend/src/main/java/com/sweet/market/{product/domain,inventory} backend/src/test/java/com/sweet/market/{inventory,store/migration}
    git commit -m "feat: add inventory aggregate foundation"

### Task 2: Add policy-aware product create and update contracts

**Files:** Modify ProductCreateRequest.java, ProductUpdateRequest.java, ProductService.java, Product.java, ErrorCode.java. Test ProductApiTest.java and ProductTest.java.

**Interfaces:** POST /api/products accepts salesPolicy, initialTotalQuantity, and lowStockThreshold. PATCH /api/products/{productId} accepts lowStockThreshold only for a persisted STOCK_MANAGED product; it never accepts a policy change.

- [ ] **Step 1: Add failing API tests**

    @Test void 활성_사업자_상점만_재고형_상품을_등록할_수_있다() throws Exception {
        createStockProduct(personalToken, personalStoreId, 5, 3)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("STORE_INVALID_TYPE"));
    }

    @Test void 재고형_상품은_초기수량과_저재고_임계값을_검증한다() throws Exception {
        createStockProduct(businessToken, businessStoreId, -1, 3).andExpect(status().isBadRequest());
    }

- [ ] **Step 2: Run the test and verify failure**

    cd backend; .\gradlew.bat test --tests 'com.sweet.market.product.ProductApiTest'

Expected: FAIL because the request has no stock policy fields.

- [ ] **Step 3: Implement conditional validation in ProductService**

    Store store = storeAccessService.requireCatalogOperator(memberId, request.storeId());
    validateSalesPolicy(store, request);
    Product saved = productRepository.save(Product.create(store, request.title(), request.description(), request.price(), request.salesPolicy(), request.lowStockThreshold(), request.initialTotalQuantity()));
    if (saved.getSalesPolicy() == ProductSalesPolicy.STOCK_MANAGED) inventoryService.initialize(saved, request.initialTotalQuantity(), memberId);

Require salesPolicy on every new creation request. Reject personal-store stock products with STORE_INVALID_TYPE and invalid/null conditional values with VALIDATION_ERROR. Update every existing test and web request fixture to send SINGLE_ITEM explicitly.

- [ ] **Step 4: Re-run focused tests and commit**

    cd backend; .\gradlew.bat test --tests 'com.sweet.market.product.ProductApiTest' --tests 'com.sweet.market.product.domain.ProductTest'
    git add backend/src/main/java/com/sweet/market/{product,common/error} backend/src/test/java/com/sweet/market/product
    git commit -m "feat: support stock-managed product creation"

### Task 3: Add operator adjustment and history APIs

**Files:** Create InventoryRepository.java, InventoryAdjustmentRepository.java, InventoryService.java, InventoryAdjustmentRequest.java, InventoryAdjustmentResponse.java. Modify StoreOperationsController.java and ErrorCode.java. Test StoreOperationsApiTest.java.

**Interfaces:** PATCH /api/store-operations/{storeId}/products/{productId}/inventory; GET /api/store-operations/{storeId}/products/{productId}/inventory/history?page=&size=.

- [ ] **Step 1: Write failing authorization and audit tests**

    @Test void 소유자와_매니저는_재고를_조정하고_이력을_조회한다() throws Exception {
        adjust(store, manager, stockProductId, 9, "RESTOCK", "입고전표-7").andExpect(status().isOk());
        getHistory(store, manager, stockProductId).andExpect(jsonPath("$.data.content[0].afterTotalQuantity").value(9));
    }

- [ ] **Step 2: Run the store-operation test and verify the endpoint is absent**

    cd backend; .\gradlew.bat test --tests 'com.sweet.market.store.StoreOperationsApiTest'

Expected: FAIL with 404 or missing response fields.

- [ ] **Step 3: Implement one transaction per adjustment**

    @PatchMapping("/{storeId}/products/{productId}/inventory")
    ApiResponse<InventoryAdjustmentResponse> adjustInventory(Authentication auth, Long storeId, Long productId, InventoryAdjustmentRequest request) {
        return ApiResponse.ok(inventoryService.adjust(memberId(auth), storeId, productId, request));
    }

Use requireCatalogOperator, load the inventory with optimistic locking, reject a resulting total below reservations and map lock/version conflicts to INVENTORY_ADJUSTMENT_CONFLICT (409). Persist the manual audit record in the same transaction. Provide no update/delete history endpoint.

- [ ] **Step 4: Re-run and commit**

    cd backend; .\gradlew.bat test --tests 'com.sweet.market.inventory.*' --tests 'com.sweet.market.store.StoreOperationsApiTest'
    git add backend/src/main/java/com/sweet/market/{inventory,store/operations,common/error} backend/src/test/java/com/sweet/market/store/StoreOperationsApiTest.java
    git commit -m "feat: add operator inventory adjustments"

### Task 4: Integrate order, payment, cart, and delivery lifecycle

**Files:** Modify OrderService.java, CartService.java, PaymentService.java, DeliveryService.java, Order.java. Test OrderTest.java, CartCheckoutApiTest.java, PaymentApiTest.java.

**Interfaces:** InventoryService.reserveForOrder(Order), releaseForPreShippingExit(Order), and commitForShipment(Order).

- [ ] **Step 1: Write failing lifecycle tests**

    @Test void 재고형_주문은_예약되고_배송_시_재고를_판매확정한다() {
        Order order = orderService.create(buyerId, stockProductId);
        assertInventory(stockProductId, 5, 1);
        deliveryService.start(buyerId, order.getId());
        assertInventory(stockProductId, 4, 0);
    }

- [ ] **Step 2: Run the lifecycle tests and verify failure**

    cd backend; .\gradlew.bat test --tests 'com.sweet.market.order.*' --tests 'com.sweet.market.cart.CartCheckoutApiTest' --tests 'com.sweet.market.payment.PaymentApiTest'

Expected: FAIL because stock is not reserved or committed.

- [ ] **Step 3: Implement policy-specific delegation**

Change Order.create so it validates the active store and product visibility for every policy, but calls Product.reserve() only when product.isSingleItem() is true. After saving a newly created stock-managed order, call reserveForOrder in the same transaction; an unavailable-stock conflict rolls back the saved order. In OrderService.cancel and PaymentService.cancel, call releaseForPreShippingExit only after the existing state transition accepts a CREATED/PAID cancellation. In DeliveryService.start, commitForShipment in the same transaction as Delivery.start. Keep cart additions unreserved and revalidate availability in add and checkout.

- [ ] **Step 4: Re-run including refund regression and commit**

    cd backend; .\gradlew.bat test --tests 'com.sweet.market.order.*' --tests 'com.sweet.market.cart.*' --tests 'com.sweet.market.payment.*' --tests 'com.sweet.market.refund.*'
    git add backend/src/main/java/com/sweet/market/{order,cart,payment,delivery} backend/src/test/java/com/sweet/market/{order,cart,payment}
    git commit -m "feat: reserve stock through order lifecycle"

### Task 5: Project availability without leaking operational stock

**Files:** Create BuyerAvailabilityResponse.java. Modify ProductRepository.java, ProductResponse.java, ProductSummaryResponse.java, CartItemResponse.java, CartItemRepository.java, StorefrontProductResponse.java, StoreCatalogProductResponse.java, StoreCatalogSummaryResponse.java. Test StorefrontApiTest.java, CartApiTest.java, StorefrontQueryOptimizationTest.java.

**Interfaces:** BuyerAvailabilityResponse(ProductSalesPolicy, AvailabilityStatus, Integer quantity); operator catalog response includes policy, totalQuantity, reservedQuantity, availableQuantity, lowStockThreshold.

- [ ] **Step 1: Add failing privacy/filter tests**

    @Test void 재고형_상품은_저재고만_구매자에게_수량을_보여준다() throws Exception {
        mockMvc.perform(get("/api/products/{id}", lowStockId))
                .andExpect(jsonPath("$.data.availability.status").value("LOW_STOCK"))
                .andExpect(jsonPath("$.data.availability.quantity").value(2))
                .andExpect(jsonPath("$.data.totalQuantity").doesNotExist());
    }

- [ ] **Step 2: Run read/query tests and verify failure**

    cd backend; .\gradlew.bat test --tests 'com.sweet.market.store.StorefrontApiTest' --tests 'com.sweet.market.cart.CartApiTest' --tests 'com.sweet.market.jpalab.StorefrontQueryOptimizationTest'

Expected: FAIL because availability projections are absent.

- [ ] **Step 3: Implement left-join constructor projections**

Use computed catalog state: stock-managed positive available quantity maps to ON_SALE; zero maps to SOLD_OUT; RESERVED remains single-item-only; HIDDEN remains excluded. Add a lightweight Inventory left join to buyer/storefront/cart/operator projections. Never fetch inventoryAdjustments. Buyer DTOs must not include total/reserved/note.

- [ ] **Step 4: Re-run budgets and commit**

    cd backend; .\gradlew.bat test --tests 'com.sweet.market.store.StorefrontApiTest' --tests 'com.sweet.market.store.StoreOperationsApiTest' --tests 'com.sweet.market.jpalab.StorefrontQueryOptimizationTest'
    git add backend/src/main/java/com/sweet/market/{inventory,product,cart,store} backend/src/test/java/com/sweet/market/{store,cart,jpalab}
    git commit -m "feat: expose inventory availability projections"

### Task 6: Implement product and store-operation web surfaces

**Files:** Modify web/src/features/products/productApi.ts, ProductCard.tsx; web/src/features/stores/storeApi.ts, storeOperationsApi.ts, StoreCatalogPanel.tsx; web/src/features/cart/cartApi.ts; web/src/pages/ProductFormPage.tsx, StoreProfilePage.tsx, MyCartPage.tsx; web/src/shared/ui/ResourceStates.tsx.

**Interfaces:** ProductSalesPolicy = SINGLE_ITEM | STOCK_MANAGED; BuyerAvailability = { status: IN_STOCK | LOW_STOCK | SOLD_OUT, quantity?: number }; InventoryAdjustmentInput = { totalQuantity:number, reason, referenceNote?:string }.

- [ ] **Step 1: Make the TypeScript contracts compile-fail first**

    export type BuyerAvailability = { status: 'IN_STOCK' | 'LOW_STOCK' | 'SOLD_OUT'; quantity?: number };
    export type ProductSalesPolicy = 'SINGLE_ITEM' | 'STOCK_MANAGED';

- [ ] **Step 2: Run build and confirm consumer errors**

    cd web; npm run build

Expected: FAIL until all response consumers use the new contract.

- [ ] **Step 3: Implement focused UI behavior**

Registration: show sales-policy selection only for active business stores; personal stores submit SINGLE_ITEM. Selecting stock-managed reveals initial total quantity and per-product threshold. Edit shows immutable policy read-only and permits threshold editing. Buyer cards/details/carts/storefront show 재고 있음, 재고 N개 남음, or 품절 only. Add catalog-row adjustment modal (resulting total/reason/optional note) and paginated history panel for owner/manager; disable commands when the store is not active. Invalidate product, cart, storefront, catalog, and summary queries after adjustment.

- [ ] **Step 4: Run build and browser checks**

    cd web; npm run build

Expected: PASS. At 390px, verify no horizontal overflow; business owner/manager can adjust; personal store has no stock option; buyer never sees operational quantities.

- [ ] **Step 5: Commit**

    git add web/src
    git commit -m "feat: add stock inventory web operations"

### Task 7: Complete regression verification and handoff

**Files:** Create docs/superpowers/handoffs/2026-07-12-milestone-23-product-sales-policy-and-stock-managed-inventory-handoff.md. Add any missing focused regression test under backend/src/test/java/com/sweet/market/inventory.

- [ ] **Step 1: Add a final immutable-history regression test**

    @Test void 재고_이력은_수정과_삭제_엔드포인트를_제공하지_않는다() throws Exception {
        mockMvc.perform(delete("/api/store-operations/{storeId}/products/{productId}/inventory/history/{id}", storeId, productId, adjustmentId))
                .andExpect(status().isNotFound());
    }

- [ ] **Step 2: Run full backend verification**

    cd backend; $env:JAVA_HOME='C:\java\jdk-21'; $env:PATH="$env:JAVA_HOME\bin;$env:PATH"; .\gradlew.bat test --rerun-tasks

Expected: BUILD SUCCESSFUL with zero failures, errors, and skipped tests.

- [ ] **Step 3: Run web and diff checks**

    cd web; npm run build; cd ..; git diff --check

Expected: build succeeds and diff check has no output.

- [ ] **Step 4: Write the factual handoff and commit**

Record final commit, test/build evidence, query budget counts, authorization/lifecycle evidence, and the M24 rule that discovery consumes availability projections without inventory history.

    git add backend/src/test docs/superpowers/handoffs/2026-07-12-milestone-23-product-sales-policy-and-stock-managed-inventory-handoff.md
    git commit -m "docs: hand off milestone 23 inventory"
