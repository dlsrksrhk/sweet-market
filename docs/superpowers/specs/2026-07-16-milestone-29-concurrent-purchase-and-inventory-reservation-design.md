# Milestone 29: Concurrent Purchase And Inventory Reservation Design

## Goal

Prevent overselling when many buyers concurrently purchase a scarce single item or a stock-managed product. Direct orders and cart checkout must create reservations safely, preserve Milestone 28 price and coupon rules, and give buyers actionable outcomes.

This milestone keeps one product per `Order`. Cart checkout may create multiple orders, but it remains all-or-nothing: if any selected cart item cannot be reserved, no orders or reservations from that checkout persist.

## Scope

- Apply concurrency-safe inventory reservation to `POST /api/orders` and cart checkout.
- Reuse the existing unpaid-order cancellation policy for reservation expiry; do not add a distributed reservation-expiration service.
- Add idempotent purchase submission using a client-supplied `Idempotency-Key`.
- Use conditional SQL updates as the production reservation baseline.
- Preserve direct-order coupon selection and keep cart checkout coupon-free.
- Return product-specific, quantity-free cart failure reasons.
- Add seller-facing inventory reservation/result visibility following existing console conventions.
- Measure conditional SQL, optimistic locking, and pessimistic locking under the same PostgreSQL scenario; use only conditional SQL in the production path.

## Out Of Scope

- Multi-item order aggregates, multi-store checkout, cart coupon allocation, partial cart checkout, backorders, warehouse allocation, and serial tracking.
- Distributed locks, queues, message brokers, external payment integration, and a new reservation scheduler.
- Changing Milestone 28 coupon eligibility, price ordering, or payment compensation policy.

## Existing Behavior To Preserve

- Direct orders optionally select one issued, eligible coupon. Cart checkout does not select coupons.
- Pricing remains `list price -> promotion -> coupon`; the server is authoritative.
- Coupon reservations remain separate from `MemberCoupon` consumption. Payment approval consumes the coupon; cancellation, definite payment failure, and expiry release its reservation.
- A zero-price direct order completes internally without calling an external payment gateway.
- Pre-shipping cancellation and definite payment failure restore stock only once through the existing inventory adjustment history.

## Purchase Submission Contract

### Idempotency Key

Direct-order and cart-checkout requests require an `Idempotency-Key` header. The client creates one key for one explicit purchase action and reuses it only when retrying that same action.

The server stores a `PurchaseRequest` keyed by `(buyer_id, idempotency_key)`. It also stores a canonical request fingerprint:

- Direct order: operation type, product ID, and optional member coupon ID.
- Cart checkout: operation type and the selected cart-item IDs normalized in ascending order.

The record stores the initial HTTP result as an immutable status, application error code, and response payload. It is retained for 48 hours, then becomes eligible for cleanup.

| Existing key state | Same fingerprint | Different fingerprint |
| --- | --- | --- |
| Processing | `409 ORDER_REQUEST_IN_PROGRESS` | `409 IDEMPOTENCY_KEY_REUSED` |
| Completed successfully | Replay the initial success response | `409 IDEMPOTENCY_KEY_REUSED` |
| Completed with a business failure | Replay the initial failure response | `409 IDEMPOTENCY_KEY_REUSED` |

Each `PROCESSING` record has a five-minute processing lease. After that time, a retry of the same fingerprint may reclaim it only while holding the request-row lock and replacing its execution token; a worker can write a terminal result only with its current token. This prevents a previous worker from overwriting a reclaimed request. The reservation transaction contains no remote payment call and must remain comfortably shorter than five minutes. A daily cleanup removes only terminal records whose 48-hour retention has elapsed; it never deletes an active processing record.

### Buyer Outcomes

Direct purchase failures distinguish unavailable and sold-out/concurrent-loss conditions without exposing exact stock. Cart checkout returns a structured list of the selected cart items that prevented checkout, each containing the cart item or product identifier, a display-safe product name, and a reason such as `SOLD_OUT` or `UNAVAILABLE`.

The cart does not delete selected items when checkout fails. The UI shows the reason beside the affected item and lets the buyer adjust the selection. A `409` processing response is treated as retryable with the same key; it is not treated as a new purchase action.

## Reservation Architecture

Both HTTP entry points delegate to one purchase-reservation application boundary. That boundary owns price revalidation, inventory reservation, order creation, and direct-order coupon reservation. It is not responsible for remote payment execution.

### Direct Order Flow

1. Claim or replay the `PurchaseRequest` for the buyer, key, and fingerprint.
2. Reload the buyer and product; verify current purchase eligibility and quote the current promotion price.
3. If requested, revalidate the coupon against that product and price using the existing Milestone 28 service.
4. Atomically reserve the product according to its sales policy.
5. Persist the order, inventory reservation history where applicable, and coupon reservation in one transaction.
6. Store the terminal purchase result. For a zero-price order, preserve the existing internal approval path after reservation succeeds.

### Cart Checkout Flow

1. Claim or replay the `PurchaseRequest` using the normalized selected cart-item IDs.
2. Validate ownership and uniqueness of all cart items.
3. Load and process their products in ascending product-ID order.
4. Revalidate each product's purchase eligibility and promotion price, then reserve it.
5. Persist all orders and inventory reservation history in one transaction. Cart checkout creates no coupon reservations.
6. Delete cart items only after every reservation and order creation succeeds.
7. If any item fails, roll back all new orders, reservations, inventory adjustments, and cart deletion; record and return the product-specific failure result.

Ascending product-ID processing is required for multi-item carts so concurrent carts acquire write contention in a consistent order. The design must not introduce a remote payment call while database reservation work is active.

## Conditional Reservation Strategy

### Single Item

Reserve a single item with a conditional status transition equivalent to:

```sql
UPDATE products
SET status = 'RESERVED'
WHERE id = :productId
  AND status = 'ON_SALE';
```

Exactly one affected row wins. Zero affected rows means the item has become unavailable or another buyer already reserved it. The winner creates the `Order`; pre-shipping cancellation restores `ON_SALE`, while completion follows the existing sold-out transition.

### Stock-Managed Product

Reserve one unit with a conditional update equivalent to:

```sql
UPDATE inventories
SET reserved_quantity = reserved_quantity + 1,
    version = version + 1
WHERE product_id = :productId
  AND total_quantity - reserved_quantity > 0;
```

Exactly one affected row records one reservation. Zero affected rows is a sold-out/concurrent-loss result. The transaction then creates the corresponding `InventoryAdjustment` reservation record. Release and shipment commitment continue to be guarded by the adjustment history, preventing duplicate recovery or double commitment.

Because this is a bulk/conditional update, the implementation must clear or refresh the persistence context before reading the affected inventory entity again. It must not rely on a stale managed `Inventory` value.

## Coupon, Payment, And Recovery Consistency

Coupon quote and reservation for a direct order occur in the same reservation transaction as the successful conditional product reservation. If the product reservation loses its race, the transaction rolls back so no coupon reservation survives.

Existing payment approval lock ordering and Milestone 28 compensation behavior remain intact. On cancellation, definite payment failure, or unpaid-order expiry, the code releases a coupon reservation if one exists and releases inventory only when the matching reservation adjustment exists and no release adjustment exists. These paths must stay idempotent.

## Seller Visibility

For stock-managed products, existing seller inventory surfaces should clearly distinguish total, reserved, and available state where those figures are already operator-safe. The order/result surfaces must not represent a failed reservation as a sale. This milestone does not redesign the store console; it adds only the reservation-focused state needed to explain current availability and order outcomes.

## Locking Study

The production baseline is conditional SQL because it places the available-quantity invariant in the database and keeps writes narrow. The milestone also includes a repeatable PostgreSQL/Testcontainers experiment for the same hot-product scenarios:

1. Conditional inventory update, used in production.
2. Existing version-based optimistic locking with bounded retries.
3. Pessimistic write locking for comparison only.

The experiment report records fixture quantity, buyer concurrency, success/failure counts, elapsed time, retries, lock waits or failures, relevant SQL, and observed trade-offs. It does not claim a universal throughput target.

## Verification

- More simultaneous direct buyers than stock quantity produce exactly the available number of successful reservations/orders.
- A single-item race produces exactly one winner.
- Concurrent cart checkouts preserve all-or-nothing behavior and use deterministic product ordering.
- Cancellation, definite payment failure, and unpaid-order expiry restore exactly one previously reserved unit and cannot inflate stock.
- A direct coupon order that loses an inventory race leaves no active coupon reservation and never consumes the coupon.
- Identical idempotency requests replay the initial success or business failure; an in-progress duplicate gets `409`; a changed request under the same key gets the key-reused conflict.
- Buyer cart errors identify the blocking item without revealing quantity.
- Focused concurrency, order, inventory, coupon, cart, and migration tests pass on PostgreSQL/Testcontainers; the full backend suite and web production build pass before delivery.

## Delivery Artifacts

- Migration and repository support for idempotent purchase requests.
- Purchase-reservation application boundary used by direct and cart entry points.
- Conditional-update repository methods and focused concurrency tests.
- Buyer cart failure presentation and seller reservation visibility.
- PostgreSQL locking comparison report with reproducible setup and observed results.
- Milestone and post-milestone handoff documents.
