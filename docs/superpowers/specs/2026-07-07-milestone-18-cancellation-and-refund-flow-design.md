# Milestone 18 Cancellation And Refund Flow Design

## Goal

Milestone 18 adds cancellation and refund handling after the review milestone.

The product goal is that buyers can cancel orders before delivery starts, request a refund after delivery completes, and have sellers or admins approve or reject that refund request without direct database changes.

The learning goal is to practice state transitions across order, payment, delivery, settlement, and product aggregates while keeping rollback-safe rules and authorization boundaries explicit.

## Context

Sweet Market currently has this order lifecycle:

```text
CREATED -> PAID -> SHIPPING -> DELIVERED -> CONFIRMED
```

`Order.cancel()` already supports canceling `CREATED` orders. `Payment.cancel()` already supports canceling paid orders before shipping. `Order.confirm()` marks a delivered order as confirmed and changes the product to `SOLD_OUT`. Settlements are created only for `CONFIRMED` orders.

Milestone 18 keeps `CONFIRMED` as the transaction close point. A buyer can request a refund only while the order is `DELIVERED`. Once the buyer confirms the purchase, refund requests are no longer allowed in this milestone.

## Scope

In scope:

- Buyers can immediately cancel `CREATED` orders.
- Buyers can immediately cancel `PAID` orders before shipping; this cancels the payment too.
- Immediate cancellation restores the reserved product to `ON_SALE`.
- Buyers can request a refund for their own `DELIVERED` order.
- A refund request requires a buyer reason.
- A seller can approve or reject refund requests for orders on their own products.
- An admin can approve or reject any refund request.
- A pending refund request blocks purchase confirmation.
- A pending or approved refund request is not eligible for settlement.
- Approved refunds mark the order as refunded and mark the payment as refunded.
- Rejected refunds return the order to `DELIVERED` so the buyer can confirm the purchase.
- Backend tests cover valid transitions, invalid transitions, authorization, duplicate requests, idempotent cancellation, and settlement blocking.
- My Orders shows cancellation and refund request entry points for buyers.
- Existing seller/admin order surfaces expose refund state enough to make the backend flow observable.

Out of scope:

- Real payment gateway refund integration.
- Partial refunds.
- Return shipping logistics.
- Dispute mediation.
- Legal audit workflow.
- Multiple refund requests for one order.
- Refund request edit or cancel.
- Refund request attachments or images.
- Dedicated refund management pages for sellers or admins.
- Automatic product relisting after a delivered-order refund.
- Review deletion, review blocking, or review moderation after refund.

## Domain Model

Add a new `refund` package with the established package structure:

- `refund.api`
- `refund.application`
- `refund.domain`
- `refund.repository`
- `refund.query`

The core entity is `RefundRequest`.

`RefundRequest` fields:

- `id`
- `order`
- `buyer`
- `reason`
- `status`
- `requestedAt`
- `handledBy`
- `handledAt`
- `rejectReason`

`RefundRequestStatus` values:

```text
REQUESTED
APPROVED
REJECTED
```

Persistence rules:

- Table name: `refund_requests`.
- Unique constraint: `order_id`.
- Required index for seller/admin lookup: `status, requested_at, id`.
- `reason` is required and should be 10 to 500 characters.
- `rejectReason` is required when a request is rejected and should be 5 to 500 characters.
- `handledBy` and `handledAt` are set only when a seller or admin approves or rejects the request.

`buyer` is derivable from `order`, but storing it on `RefundRequest` keeps buyer-facing reads simple and makes ownership checks explicit.

## Order And Payment State

Extend `OrderStatus` with:

```text
REFUND_REQUESTED
REFUNDED
```

The main order lifecycle becomes:

```text
CREATED -> PAID -> SHIPPING -> DELIVERED -> CONFIRMED
                         |
                         -> REFUND_REQUESTED -> REFUNDED
                         -> REFUND_REQUESTED -> DELIVERED
```

The second `REFUND_REQUESTED -> DELIVERED` path represents rejection.

Extend `PaymentStatus` with:

```text
REFUNDED
```

`PaymentStatus.CANCELED` remains the payment state for pre-delivery cancellation. `PaymentStatus.REFUNDED` represents a post-delivery refund approval.

## Immediate Cancellation

Endpoint:

```text
POST /api/orders/{orderId}/cancel
```

Requires buyer authentication.

Behavior for `CREATED` orders:

1. Load the order with buyer and product.
2. Reject missing orders.
3. Reject orders not owned by the authenticated buyer.
4. Change the order to `CANCELED`.
5. Restore the product reservation to `ON_SALE`.
6. Return the order response.

Behavior for `PAID` orders:

1. Load the payment with order and product.
2. Reject missing payments.
3. Reject orders not owned by the authenticated buyer.
4. Cancel the payment through the local payment gateway abstraction.
5. Change the payment to `CANCELED`.
6. Change the order to `CANCELED`.
7. Restore the product reservation to `ON_SALE`.
8. Return the order response.

Idempotency:

- Repeating cancellation for an already `CANCELED` order returns the current canceled order response.
- Repeating payment cancellation for an already `CANCELED` payment returns the current canceled result through the order cancellation endpoint.

Cancellation is rejected for:

- `SHIPPING`
- `DELIVERED`
- `REFUND_REQUESTED`
- `REFUNDED`
- `CONFIRMED`

## Refund Request Creation

Endpoint:

```text
POST /api/orders/{orderId}/refund-requests
```

Requires buyer authentication.

Request body:

```json
{
  "reason": "상품 상태가 설명과 달라 환불을 요청합니다."
}
```

Behavior:

1. Validate the request body.
2. Load the order with buyer and product.
3. Reject missing orders.
4. Reject orders not owned by the authenticated buyer.
5. Reject orders that are not `DELIVERED`.
6. Reject orders that already have a refund request.
7. Create a `RefundRequest` with status `REQUESTED`.
8. Change the order status to `REFUND_REQUESTED`.
9. Return the refund request response.

Duplicate request handling:

- One order can have at most one refund request.
- A duplicate request returns a conflict response.
- A database unique constraint race is mapped to the same conflict response.

## Seller Refund Handling

Seller endpoints:

```text
POST /api/seller/refund-requests/{refundRequestId}/approve
POST /api/seller/refund-requests/{refundRequestId}/reject
```

Requires seller authentication.

Reject request body:

```json
{
  "rejectReason": "상품 설명과 다른 부분을 확인할 수 없습니다."
}
```

Seller authorization:

- The seller can handle only refund requests for orders on products they own.
- A non-owner seller receives an access denied response.

Approve behavior:

1. Load the refund request with order, product, seller, payment, and handler.
2. Reject missing requests.
3. Reject requests not owned by the seller.
4. Reject requests not in `REQUESTED`.
5. Mark the refund request `APPROVED`.
6. Mark the payment `REFUNDED`.
7. Mark the order `REFUNDED`.
8. Keep the product out of automatic resale.
9. Return the refund request response.

Reject behavior:

1. Validate `rejectReason`.
2. Load the refund request with order, product, seller, and handler.
3. Reject missing requests.
4. Reject requests not owned by the seller.
5. Reject requests not in `REQUESTED`.
6. Mark the refund request `REJECTED`.
7. Change the order back to `DELIVERED`.
8. Return the refund request response.

## Admin Refund Handling

Admin endpoints:

```text
POST /api/admin/refund-requests/{refundRequestId}/approve
POST /api/admin/refund-requests/{refundRequestId}/reject
```

Requires admin authentication.

Admin handling uses the same approve and reject state transitions as seller handling, but without seller ownership restriction. Admins can handle refund requests across all sellers.

## Confirmation And Settlement Rules

Purchase confirmation remains allowed only for `DELIVERED` orders.

Purchase confirmation is rejected for:

- `REFUND_REQUESTED`
- `REFUNDED`
- `CANCELED`
- `CONFIRMED`

Settlement creation remains allowed only for `CONFIRMED` orders.

Settlement creation is rejected for:

- `CREATED`
- `PAID`
- `SHIPPING`
- `DELIVERED`
- `REFUND_REQUESTED`
- `REFUNDED`
- `CANCELED`

This keeps pending refund requests from being finalized while also keeping refunded orders out of seller payout flows.

## Query And Response Changes

Buyer order responses should include:

- `refundStatus`
- `refundRequestedAt`
- `refundHandledAt`
- `refundRejectReason`

These fields are absent or null when an order has no refund request.

Seller/admin order responses should include enough refund state to understand whether an order is refundable, pending refund handling, refunded, or rejected. This milestone can expose the same summary fields on existing order read models instead of adding a dedicated refund list page.

Refund request responses should include:

- `id`
- `orderId`
- `productId`
- `productTitle`
- `buyerId`
- `reason`
- `status`
- `requestedAt`
- `handledById`
- `handledAt`
- `rejectReason`

## Web Scope

Buyer web changes:

- My Orders shows a cancel action for `CREATED` and `PAID` orders.
- My Orders shows a refund request action for `DELIVERED` orders without a refund request.
- The refund request action collects a reason.
- My Orders shows a refund status badge for `REQUESTED`, `APPROVED`, and `REJECTED`.
- My Orders hides or disables purchase confirmation while a refund request is `REQUESTED`.
- Rejected refund requests show the rejection reason and allow purchase confirmation because the order returns to `DELIVERED`.

Seller/admin web changes:

- Existing seller/admin order surfaces show refund state if that order has a refund request.
- Dedicated seller/admin refund queues are out of scope for this milestone.

## Error Handling

Add error codes for:

- Refund request not found.
- Refund request access denied.
- Refund request not allowed for the current order state.
- Duplicate refund request.
- Refund request handling not allowed for the current refund state.
- Refund reject reason validation failure should use the existing validation error path.

Services should translate domain `IllegalStateException` cases into specific `BusinessException` error codes.

## Testing

Domain tests:

- A delivered order can enter `REFUND_REQUESTED`.
- A requested refund can be approved and move the order to `REFUNDED`.
- A requested refund can be rejected and move the order back to `DELIVERED`.
- A refunded payment uses `PaymentStatus.REFUNDED`.
- Purchase confirmation rejects `REFUND_REQUESTED` and `REFUNDED`.
- Settlement creation rejects non-`CONFIRMED` orders, including `REFUND_REQUESTED` and `REFUNDED`.

API tests:

- Buyer cancels a `CREATED` order and product returns to `ON_SALE`.
- Buyer cancels a `PAID` order and payment becomes `CANCELED`.
- Repeating cancellation for a canceled order is idempotent.
- Buyer cannot cancel `SHIPPING`, `DELIVERED`, `REFUND_REQUESTED`, `REFUNDED`, or `CONFIRMED` orders.
- Buyer creates a refund request for a delivered order.
- Buyer cannot request refund for another buyer's order.
- Buyer cannot request refund before delivery or after confirmation.
- Buyer cannot create duplicate refund requests.
- Seller approves a refund request for their own product.
- Seller rejects a refund request for their own product.
- Seller cannot handle another seller's refund request.
- Admin approves any refund request.
- Admin rejects any refund request.
- Non-admin cannot call admin refund endpoints.
- Purchase confirmation is blocked while refund is requested.
- Settlement creation and settlement batch processing skip or reject refund-requested and refunded orders.

Web checks:

- `npm run build` passes.
- My Orders renders cancel and refund actions for the correct order states.
- My Orders does not show purchase confirmation while refund is pending.

All new JUnit `@Test` method names must be Korean with underscores.

## Verification

Backend:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Web:

```powershell
cd web
npm run build
```

Repository hygiene:

```powershell
git diff --check
```

Do not stage, overwrite, reset, or discard the existing local-only `backend/src/main/resources/application.yaml` change.

## Follow-Up Candidates

Good future milestones after the first cancellation and refund implementation:

- Dedicated seller refund queue.
- Dedicated admin refund management page.
- Return shipping workflow.
- Refund request cancellation by buyer.
- Product relisting after refund.
- Refund-related review rules.
- Refund images or attachments.
- Dispute mediation and audit notes.

These follow-ups are outside Milestone 18.

## Self-Review

- Scope is one cancellation and refund milestone.
- The design keeps `CONFIRMED` as the transaction close point.
- Refund requests are available only from `DELIVERED`.
- Pending refund requests block purchase confirmation and settlement.
- Approved refunds do not automatically relist products.
- The design keeps roadmap, spec, plan, and handoff concerns separate.
