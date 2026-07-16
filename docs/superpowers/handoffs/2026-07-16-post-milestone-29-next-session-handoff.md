# Post-Milestone 29 Next Session Handoff

## Start With M30

M30 is the next milestone. Begin it in a fresh worktree with design and planning; do not expand M29 incidentally.

## Invariants That M30 Must Preserve

- Purchase endpoints require a non-blank `Idempotency-Key`; replay returns the originally persisted response.
- A reused key with a different fingerprint remains a conflict, and an active identical request remains `409 ORDER_REQUEST_IN_PROGRESS`.
- Single-item and stock reservations must remain conditional database updates. Do not pre-check availability and then update.
- Cart purchase keeps the ascending store/product lock order and preserves cart rows with per-item failure reasons on unsuccessful checkout.
- Coupon and stock compensation occurs once only. Losing coupon requests must not consume a coupon.
- Operator catalog inventory fields are `totalQuantity`, `reservedQuantity`, and `availableQuantity`; buyer availability must be based on available quantity.

## Verification Note

M29’s backend suite has one documented non-M29 residual: storefront query optimization expects an on-sale count of 20 and observes 22. Investigate it separately; do not change M29 reservation logic to make that expectation pass.

Start local services when needed:

```powershell
cd backend
docker compose up -d
```
