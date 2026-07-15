# Post-Milestone 27 Next Session Handoff

## Start Here

`main` and `origin/main` include M27 through `efb2d1e`.

M27 added optional first-come limits to coupon campaigns. Limited campaigns use Redis Lua reservation on the normal path, a conditional database count increment as the final capacity guard, and a database pessimistic-lock fallback when Redis is unavailable. This work is complete; do not redesign or replace it while starting the next milestone unless a concrete regression is found.

The next recommended milestone is **M28: coupon redemption and order discount application**. It has not been designed or implemented.

## First Actions In A New Session

1. Preserve these existing user-local changes in the main worktree:
   - `backend/src/main/resources/application.yaml` — local JWT default only; do not commit it accidentally.
   - `docs/superpowers/handoffs/2026-07-08-post-milestone-18-next-session-handoff.md` — untracked user document.
2. Start M28 in a new worktree and branch, for example `codex/milestone-28-coupon-redemption-and-order-discounts`.
3. Run brainstorming first. Confirm M28 policy decisions before coding, then write a design document and implementation plan.
4. Keep M27 browser QA separate from M28 scope. It is a small outstanding manual verification item, not an implementation blocker.

## M27 State To Preserve

### Coupon Issuance

- `CouponCampaign.issueLimit` is nullable: `null` means unlimited; a supplied value is at least one and can change only in `DRAFT`.
- `CouponCampaign.issuedCount` is cumulative. Coupon use, expiry, campaign pause, and campaign end must never decrease it.
- A limited campaign reserves capacity through `RedisCouponIssuanceGate` and Lua resources under `backend/src/main/resources/redis/`.
- The database remains authoritative: `CouponCampaignRepository.incrementLimitedIssuedCount(...)` prevents durable over-issuance even after Redis cache loss or a Redis partition.
- Redis unavailability uses `CouponIssueTransactionService.issueWithPessimisticLock(...)`.
- A buyer who already owns a campaign coupon receives the existing coupon successfully even after sellout, pause, or end. A new buyer after sellout receives `409 COUPON_ISSUE_LIMIT_EXCEEDED`.
- Do not turn Redis into the source of coupon history or replace the database count/unique campaign-member constraint.

### Existing Coupon Model

- `MemberCoupon` is an issued-policy snapshot with `ISSUED` and reserved `USED` status values.
- M27 does **not** attach a coupon to an order, calculate an order discount, decrement issuance count after use, or modify payment, cancellation, refund, settlement, or report prices.
- The current order constraint remains one product per order. Preserve it unless M28 explicitly designs a change.

### Buyer And Operator Contracts

- Owner campaign responses expose `issueLimit`, `issuedCount`, and `remainingIssueCount`; unlimited values are explicit `null` and display as `무제한`.
- Buyer discovery returns active sold-out campaigns with `soldOut: true`; the UI disables new claims and displays `선착순 마감`.
- A claimed campaign takes precedence over sold-out display and shows `발급 완료`.

## M28 Discovery Questions

Resolve these during brainstorming before any implementation:

1. Which issued coupon can be selected for a single-product order, and how do campaign owner, scope, selected targets, minimum purchase amount, expiry, lifecycle, and `stackable` affect eligibility?
2. How does a coupon discount compose with the existing M25 promotion-price snapshot? Define the exact calculation order, rounding, maximum discount, and final-price floor.
3. How is a coupon atomically reserved or marked `USED` with order/payment creation so concurrent requests cannot use it twice?
4. What happens to coupon state and persisted price snapshots when payment approval fails, an order is cancelled, or a refund is accepted? Do not assume reissue or restoration without an explicit policy.
5. Which order, payment, refund, settlement, and reporting projections need a persisted coupon discount snapshot rather than a read from mutable campaign policy?

Likely M28 additions include an order-to-member-coupon reference, immutable coupon-discount snapshots on the order, eligibility/calculation services, transactional single-use control, request/response changes, and buyer UI coupon selection. Treat this as a fresh design, not a direct continuation of M27 implementation details.

## Verification Baseline

The following completed after M27 was merged into `main`:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

Result: `BUILD SUCCESSFUL` on merged `main` (3 minutes 14 seconds).

```powershell
cd web
npm run build
```

Result: successful production build. Vite reports an existing 500 kB chunk-size warning; it is not a build failure.

Docker Desktop was required for PostgreSQL and Redis Testcontainers. Local Compose now defines both `market-postgres` and `market-redis` in `backend/docker-compose.yml`; start them with `cd backend; docker compose up -d` when local services are needed. The containers were stopped after verification, so a new session may need to start them again.

## Outstanding Manual Check

The headless browse daemon did not start during M27 verification, so these UI checks remain unexecuted:

1. Blank issue limit serializes as omitted and displays `발급 무제한`.
2. A draft campaign with limit 3 displays `발급 0 / 3` and `잔여 3`.
3. A sold-out unclaimed card displays `선착순 마감` with a disabled claim button.
4. A sold-out claimed card displays `발급 완료` instead.

If browser tooling is available, verify these before or alongside M28 work. Record the result in the next handoff; do not block M28 design on it.
