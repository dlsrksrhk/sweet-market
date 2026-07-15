# Post-Milestone 28 Next Session Handoff

## Start Here

`main` and `origin/main` include M28 through merge commit `6f6fc05`.

M28 adds coupon redemption for direct single-product orders. A buyer can select zero or one eligible issued coupon; the order snapshots the coupon ID and discount amount. Coupon capacity issuance and first-come controls from M27 remain unchanged.

The next product milestone has not been selected or designed. Start it in a new worktree and run brainstorming before implementation. Do not extend M28 policy without a concrete regression or an explicitly designed follow-up.

## Preserve User-Local Changes

The main worktree intentionally has these user-local changes. Do not stage or commit them accidentally.

- `backend/src/main/resources/application.yaml` — local JWT/default configuration.
- `docs/superpowers/handoffs/2026-07-08-post-milestone-18-next-session-handoff.md` — untracked user document.

## M28 Behavior to Preserve

### Eligibility and pricing

- A direct order (`POST /api/orders`) accepts optional `memberCouponId`; cart checkout remains unchanged.
- A buyer selects zero or one eligible coupon. Eligibility uses the issued `MemberCoupon` snapshot, not mutable campaign policy.
- A valid issued coupon remains usable after its campaign is paused or ended, until `validUntil`.
- Price order is `list price → promotion → coupon`. Minimum purchase amount is evaluated after promotion.
- `stackable=false` blocks a coupon when an active promotion applies. `stackable=true` permits the combination.
- Fixed discounts cannot exceed the promotion-adjusted price. Percentage discounts round down, respect `maxDiscountAmount`, and are safe at `long` boundaries. Final price cannot be negative.
- A zero-price order completes through internal payment (`INTERNAL_ZERO_AMOUNT:{orderId}`) without calling the external payment gateway.

### Reservation and payment state

- `coupon_reservations` holds `RESERVED`, `CONSUMED`, `RELEASED`, and `EXPIRED` history; `MemberCoupon` itself is `ISSUED` or `USED`.
- Order creation locks the coupon, creates a 30-minute reservation, and stores coupon price snapshots in the same transaction as order and inventory reservation.
- Payment approval locks in the order → reservation → coupon order. Success consumes the reservation and marks the coupon `USED`.
- Definite gateway failure uses one `REQUIRES_NEW` compensation transaction to release the reservation, cancel the created order, and restore inventory together.
- Payment cancellation or an approved refund never restores a consumed coupon. A real remote payment integration must later distinguish definite rejection from an unknown timeout result; the current `FakePaymentGateway` has definite failures only.
- Buyer cancellation before payment releases the reservation. The expiry scheduler also releases a reservation after 30 minutes, cancels the created order, and restores inventory. It locks order → reservation and processes each expired reservation in an independent transaction.

### API and UI snapshots

- Coupon price snapshots are exposed from buyer order, payment, refund, settlement, and administrator order/settlement responses as `memberCouponId` and `couponDiscountAmount`.
- `GET /api/me/coupons/eligible?productId=...` returns only eligible coupons and computed discount/final price.
- The product detail page scopes eligible-coupon cache by authenticated member and product. It blocks direct checkout while the buyer's eligibility query loads or fails, avoiding accidental full-price submission.
- Order actions invalidate coupon caches so wallet and eligibility state refresh after consumption or release.

## Database Notes

- Migration: `backend/src/main/resources/db/migration/V12__add_coupon_redemptions_and_order_coupon_snapshots.sql`.
- In normal schemas containing both `orders` and `member_coupons`, V12 creates reservation constraints/indexes and order snapshot columns.
- Some focused legacy migration verification schemas lack those tables. V12 safely skips in that narrow path; do not remove this guard without updating the migration-test model.

## Verification Baseline

Latest merged HEAD verification:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Result: `BUILD SUCCESSFUL` in 4 minutes 30 seconds.

```powershell
cd web
npm run build
```

Result: successful. Vite retains an existing approximately 520 kB pre-compression chunk warning; it is not a build failure.

## Outstanding Manual QA

Browser automation was unavailable during M27 and authenticated end-to-end coupon UI QA was not completed during M28. These are manual verification items, not known implementation failures.

1. M27: unlimited issue limit displays `발급 무제한`.
2. M27: a limit-3 draft displays `발급 0 / 3`, `잔여 3`.
3. M27: sold-out unclaimed campaign displays `선착순 마감` and disables claim.
4. M27: sold-out claimed campaign displays `발급 완료`.
5. M28: a buyer sees only eligible coupons on a product detail page, can clear or choose one, and sees the expected final price.
6. M28: a coupon order consumes the coupon after payment; a failed payment or pre-payment cancellation returns it; a zero-price order does not invoke the external gateway.

Start local services when needed:

```powershell
cd backend
docker compose up -d
```

The Compose file provides PostgreSQL and Redis. Docker Desktop is required for Testcontainers.
