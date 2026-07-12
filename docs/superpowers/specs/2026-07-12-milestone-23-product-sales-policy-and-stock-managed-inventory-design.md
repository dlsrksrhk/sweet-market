# Milestone 23 Product Sales Policy And Stock-Managed Inventory Design

## Purpose

Milestone 23 keeps the existing one-off used-goods flow while allowing active business stores to sell repeatable inspected inventory. It introduces an explicit sales policy, auditable inventory operations, and buyer-safe availability information without changing M22 store ownership, authorization, privacy, or query-shape boundaries.

## Confirmed Decisions

- Only an active `BUSINESS` store may create a `STOCK_MANAGED` product. Personal stores create `SINGLE_ITEM` products only.
- `Inventory` is the single authority for stock-managed availability. Product status must not independently represent stock-managed sold-out state.
- Buyer-facing stock is private by default: show `재고 있음`, `품절`, or `재고 N개 남음` only when the product-specific low-stock threshold is reached.
- Each stock-managed product owns a positive low-stock threshold. The initial total quantity may be zero or greater.
- Operators adjust the resulting total quantity, not a delta. The resulting total cannot be lower than active reservations.
- Manual adjustment uses a standard reason (`RESTOCK`, `STOCKTAKE`, `DAMAGE_OR_DISPOSAL`, `RETURN_RESTOCK`, or `OTHER`) and an optional reference note.
- Both active `OWNER` and `MANAGER` memberships can adjust stock and view inventory history.
- A stock unit is released only for a pre-shipping cancellation or payment failure. Shipment converts one reservation into an irreversible sale; a later refund never restores stock automatically.

## Domain Model

### Product

`Product` gains an immutable `ProductSalesPolicy`:

```text
SINGLE_ITEM | STOCK_MANAGED
```

Existing rows migrate to `SINGLE_ITEM`. A product's policy cannot be changed after creation. `lowStockThreshold` is required only for `STOCK_MANAGED` and remains product metadata; operators may update it through normal product editing without changing the policy.

`SINGLE_ITEM` retains its current status lifecycle:

```text
ON_SALE -> RESERVED -> SOLD_OUT
```

For `STOCK_MANAGED`, `ProductStatus` remains the visibility boundary: `HIDDEN` prevents buying and public display. A visible stock-managed product does not transition to `RESERVED` or `SOLD_OUT`; availability comes exclusively from inventory quantities.

### Inventory

Each stock-managed product has exactly one `Inventory` row.

```text
totalQuantity: non-negative integer
reservedQuantity: non-negative integer
availableQuantity: totalQuantity - reservedQuantity (derived)
version: optimistic-lock version
```

The aggregate owns these commands:

- initialize stock for a newly created stock-managed product;
- adjust the resulting total quantity, rejecting totals below `reservedQuantity`;
- reserve one available unit for a new order;
- release one reservation for an eligible pre-shipping cancellation or payment failure;
- commit one reservation at shipment by decrementing both total and reserved quantities.

`InventoryService` coordinates these commands with store access checks and order lifecycle transitions. It is not a general product-edit service.

### Inventory History

`InventoryAdjustment` is an append-only audit entity. Manual entries retain before/after total quantity, delta, reason, optional note, operator, and occurrence time. Order-driven entries retain a change type, before/after quantities, the related order, and sufficient actor/context information to explain reservation, release, or shipment commitment. The history is never mapped as an eager collection on `Product` or `Inventory`.

## Availability And Order Flow

Buyer responses expose an availability projection rather than operational quantities:

```text
IN_STOCK  -> 재고 있음
LOW_STOCK -> 재고 N개 남음
SOLD_OUT  -> 품절
```

For stock-managed products, `LOW_STOCK` applies when `availableQuantity` is positive and no greater than `lowStockThreshold`; `SOLD_OUT` applies at zero. Total, reserved, and adjustment-note values are never buyer-facing.

Cart creation does not reserve stock. Cart reads, cart checkout, and direct order creation revalidate product visibility, active-store status, and availability. The sequential stock-managed lifecycle is:

```text
new order          total=T, reserved=R -> total=T, reserved=R+1
pre-shipping exit  total=T, reserved=R -> total=T, reserved=R-1
shipment           total=T, reserved=R -> total=T-1, reserved=R-1
```

The existing single-item order behavior remains unchanged. A stock-managed product with no available unit produces the existing purchase-unavailable response; a manual adjustment blocked by reservations or an optimistic-lock conflict produces a `409` response.

Public storefront category filtering uses a computed catalog state for stock-managed products: visible positive-stock products belong to `ON_SALE`, zero-stock products to `SOLD_OUT`, and `RESERVED` remains single-item-only. Hidden products remain excluded. Operator responses provide policy and operational quantity fields separately, avoiding a false interpretation of product status as inventory state.

## API And Web Surface

`POST /api/products` accepts `salesPolicy`, plus `initialTotalQuantity` and `lowStockThreshold` for stock-managed products. The server rejects stock fields for single items, invalid quantities or thresholds, non-business stores, inactive stores, and unauthorized operators. Product update requests cannot change `salesPolicy`; later quantity changes use the dedicated inventory command.

Store operations adds these role-scoped endpoints:

```text
PATCH /api/store-operations/{storeId}/products/{productId}/inventory
GET   /api/store-operations/{storeId}/products/{productId}/inventory/history
```

The adjustment request carries resulting total quantity, standard reason, and an optional note. History is paginated. Both endpoints require an active `OWNER` or `MANAGER` membership and respect the existing suspended-store read-only boundary.

The product registration experience shows a sales-policy segmented control. Personal-store users can select only single item. Selecting stock-managed for an eligible business store reveals initial total quantity and a product-specific low-stock threshold. Product editing renders the immutable policy as read-only and permits a stock-managed product's low-stock threshold to be updated. The existing store catalog adds stock policy and quantity columns plus stock-managed-only `재고 조정` and `이력 보기` actions. The adjustment modal asks for resulting total quantity, reason, and optional note; the history view is a paginated table. Buyer cards, details, carts, wishlists, and storefronts show only the availability projection.

## Persistence And Query Boundaries

The migration adds a non-null `sales_policy` to `products`, backfills existing rows as `SINGLE_ITEM`, and adds the stock-managed threshold constraint. It creates `inventories` with a unique product foreign key, quantity integrity constraints, and a version column. It creates immutable `inventory_adjustments` with an index by product and occurrence time.

Catalog queries use a lightweight inventory join or projection only where availability is needed. They never load inventory history. M22 query budgets remain a regression gate: public storefront header plus first product page must remain bounded, operator store list/summary/first catalog page must remain bounded, and both paths must retain zero collection fetches.

## Verification

- Existing products migrate to `SINGLE_ITEM` and retain their reservation, cancellation, and confirmation behavior.
- Only active business-store operators can create stock-managed products; invalid initial stock or threshold values fail validation.
- Manual adjustment and history access succeed for owner and manager, and fail for outsiders or suspended stores.
- Sequential stock orders reserve exactly one unit, pre-shipping cancellation/payment failure releases exactly one unit, and shipment commits exactly one reserved unit.
- Manual totals below reservations and ordinary optimistic-lock conflicts return `409` without a partial audit record.
- Product, detail, cart, wishlist, storefront, and operator responses present consistent availability while protecting internal quantities and notes.
- Inventory history is immutable, paginated, and attributes manual changes to the responsible operator.
- Focused API/domain/query tests, the full backend test suite, the web build, and diff hygiene checks all pass. New JUnit test method names use Korean with underscores.

## Explicitly Out Of Scope

- High-contention inventory algorithms, load testing, and the M29 locking comparison.
- Product variants, SKUs, warehouse locations, serial tracking, backorders, bulk import, and ERP synchronization.
- Promotion/coupon pricing, global search, and M24 discovery work.
