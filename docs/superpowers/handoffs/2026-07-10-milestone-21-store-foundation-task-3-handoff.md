# Milestone 21 Store Foundation — Task 3 Handoff

## Current State

- Worktree: `C:\dev\study\sweet-market\.worktrees\milestone-21-task-3-product-store-ownership`
- Branch: `codex/milestone-21-task-3-product-store-ownership`
- Product ownership has moved from `Product.seller` to required `Product.store`; order records retain a required historical `Order.seller` snapshot.
- The response-consistency audit is complete: product and order detail/summary responses now each return the required store and compatible seller fields.

## Database Migration And Schema Checks

`V3__complete_product_store_ownership.sql` completes the ownership transition after the V1/V2 store foundation:

- It refuses to continue if an existing product has a null or orphaned `store_id`, then adds `fk_products_store`, makes `products.store_id` non-null, and creates `idx_products_store_status_id` when `products.status` exists.
- It backfills a null `orders.seller_id` from the migrated product store's immutable owner. It then refuses any remaining null seller, adds `fk_orders_seller`, makes `orders.seller_id` non-null, and creates `idx_orders_seller_id`.
- It drops the retired `products.seller_id` only after the product-store and order-seller transition succeeds.

The legacy-schema Spring Boot/Flyway test verifies V3 reaches version 3, preserves the product-store link, snapshots the historical order seller, and removes `products.seller_id`. The fresh-PostgreSQL boot test verifies `products.store_id`, `orders.seller_id`, both foreign keys, and both indexes after Flyway plus JPA schema reconciliation.

## Product Command Contract

- Product creation requires the caller to include an explicit `storeId`.
- The selected store must allow the authenticated member to operate its catalog. Product update and hide authorize against the product's existing store; they do not transfer ownership.
- A product's current seller compatibility identity is the immutable owner of its store. An order's seller compatibility identity is the `Order.seller` value captured when the order is created, so refunds, settlements, and seller reports retain historical member-seller data.

## Required Response Contract

Product detail and summary responses must each expose:

- `storeId`, `storeName`, `storeType`
- legacy-compatible `sellerId`, `sellerNickname`, derived from the product store's immutable owner

Order detail and summary responses must each expose:

- `storeId`, `storeName`, `storeType`, derived from the ordered product's store
- `sellerId`, `sellerNickname`, from the immutable `Order.seller` snapshot

## Response Contract Completion

- `ProductResponse` and `ProductSummaryResponse` derive `sellerId` and `sellerNickname` from `product.store.ownerMember` while retaining the store fields. The public and seller-product summary projections join the store owner so every summary supplies the same identity.
- `OrderResponse` and `OrderSummaryResponse` derive `storeId`, `storeName`, and `storeType` from `order.product.store` while continuing to use `Order.seller` for the historical seller fields.
- Real MockMvc regressions cover public product detail/list and authenticated order detail/list from one seller/store fixture, asserting every required response field and their matching values.

## Verification

The pre-audit focused suite passed with JDK 21 and `JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`:

```powershell
cd C:\dev\study\sweet-market\.worktrees\milestone-21-task-3-product-store-ownership\backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests 'com.sweet.market.product.*' --tests 'com.sweet.market.order.*' --tests 'com.sweet.market.refund.*' --tests 'com.sweet.market.settlement.*' --tests 'com.sweet.market.seller.report.*'
```

The response-contract tests initially failed at the missing fields, then passed after the four DTO mappings and two product summary projections were completed. The focused response suite, the full focused Task 2 suite, and the full backend suite all pass with the command environment above. `git diff --check` is clean for the Task 2 changes.

## Explicit Task 5 UI Boundary

Task 3 does not modify the web application. Store-selection controls, TypeScript response contracts, product cards and detail views, store routes, and My Store/admin screens remain Task 5 work. The UI must send the selected `storeId` for product creation and consume the completed store plus legacy seller response fields; it must not infer store identity from `sellerId`.
