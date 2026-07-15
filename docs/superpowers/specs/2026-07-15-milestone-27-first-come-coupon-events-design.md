# Milestone 27: First-Come Coupon Events And Concurrent Issuance Design

## Purpose

M27 adds optional first-come issuance limits to the M26 coupon campaign model. A limited campaign must never issue more coupons than its configured limit, including when many buyers claim it concurrently from multiple application instances. Existing campaign ownership, lifecycle, issued-coupon snapshots, and idempotent claims remain intact.

## Scope

- Add an optional per-campaign issuance limit and a durable cumulative issuance count.
- Use Redis Lua scripts as the normal admission path for limited, active campaigns.
- Confirm every admitted claim synchronously in the database before returning success.
- Compensate a Redis reservation when its database confirmation fails.
- Fall back to a database pessimistic-lock path when Redis is unavailable.
- Surface issue limit, issued count, and remaining count to operators.
- Keep sold-out campaigns in the buyer discovery list and mark them as unavailable for new claims.
- Add concurrency, boundary, compensation, Redis-recovery, and fallback tests.

## Explicitly Out Of Scope

- Coupon selection, discount calculation, and coupon redemption in checkout or orders; these remain M28 work.
- Queue-based asynchronous issuance, message brokers, notifications, or waiting rooms.
- Redis as the durable source of coupon history or issuance audit data.
- Changing the current single-product order constraint.

## Campaign Issuance Policy

`CouponCampaign` gains the following persisted fields:

- `issueLimit`: nullable positive integer. `null` means unlimited issuance.
- `issuedCount`: non-null cumulative integer, initialized to zero.

The count includes every successfully issued `MemberCoupon` permanently, regardless of later coupon use, expiry, or campaign pause/end. A database check enforces `issuedCount >= 0` and, when a limit exists, `issuedCount <= issueLimit`. Existing campaigns migrate to `issueLimit = null` and `issuedCount = 0`.

Operators may set a limit only in `DRAFT`. Once a campaign leaves draft, `issueLimit` is immutable even if other M26 scheduled-campaign edits are still allowed. A configured limit must be at least one; zero is invalid.

## Issuance Semantics

`POST /api/coupon-campaigns/{campaignId}/claim` retains its path and successful response contract.

1. The service first reads an existing campaign/member coupon. If present, it returns that coupon successfully. This remains true after the campaign sells out, pauses, or ends.
2. An unlimited campaign uses the existing transaction-safe idempotent claim behavior.
3. A limited campaign uses the Redis admission protocol below, then commits the database issue transaction before returning success.
4. A new claimant after all slots are committed or reserved receives `409 Conflict` with `COUPON_ISSUE_LIMIT_EXCEEDED`.
5. If campaign lifecycle or issue-window eligibility fails, the existing lifecycle conflict remains the result rather than a sold-out result.

The database still enforces `unique (coupon_campaign_id, member_id)`. A duplicate-key race returns the committed existing coupon and must not consume an additional slot.

## Redis Admission Protocol

Redis is an admission and concurrency-control layer, not the source of truth. It is used only for limited campaigns while Redis is reachable.

`CouponIssuanceGate` separates the application service from the Redis implementation and the database fallback. The Redis implementation uses Lua so each operation is atomic in Redis:

- a campaign counter representing committed issues plus active short-lived reservations;
- a member reservation keyed by campaign and member, containing an opaque reservation token;
- an expiry-indexed pending-reservation structure so a later script invocation can release abandoned reservations.

Before a campaign's first admission after a missing or expired Redis cache key, an initialization script seeds its counter from the durable `issuedCount`. Campaign keys expire after the issue window with a short recovery grace period. Every admission script first releases expired pending reservations, then checks the caller's member state, then compares the counter with `issueLimit`, and finally creates exactly one reservation token when capacity remains.

The normal limited-claim flow is:

1. Acquire a Redis reservation through Lua. The result is `RESERVED`, `ALREADY_ISSUED`, `IN_PROGRESS`, or `SOLD_OUT`.
2. For `RESERVED`, start a new database transaction. Revalidate the campaign's active issue window, conditionally increment `issuedCount` only while the persisted count remains below the limit, and save the `MemberCoupon` snapshot.
3. On database commit, finalize the matching Redis reservation. The reserved counter remains consumed and the member state becomes issued until key expiry.
4. On a database failure or capacity/lifecycle rejection, release only the matching reservation token. This decrements the Redis counter exactly once.
5. For `IN_PROGRESS`, briefly resolve the prior request: return its newly committed coupon when present, or retry admission once its reservation has been released or expired.

The conditional database increment is a second, mandatory limit guard. It prevents overselling if Redis restarts, loses keys, or returns after a network partition with stale state. If this guard rejects a reservation, the service reloads the campaign state, releases the reservation, and returns lifecycle or sold-out semantics as appropriate.

## Redis Failure Fallback

When Redis is unavailable before admission, the service uses a database pessimistic-write lock on the campaign row. Inside one transaction it checks existing issuance, validates campaign eligibility, checks the limit, increments `issuedCount`, and stores the coupon. This preserves availability and the exact same success, idempotency, and `409` capacity behavior.

For an uncertain Redis network outcome, the service treats the database as authoritative. A possible orphaned Redis reservation is safe because the database guard prevents over-issuance; its token expires and is reclaimed by a subsequent script invocation. Redis state is reseeded from `issuedCount` after cache loss. No background reconciliation job is required for M27 because durable counts and coupons are always written synchronously, while temporary reservations are self-cleaning.

## API And Web Experience

Create and update payloads gain an optional `issueLimit` field. Owner campaign summary and detail responses add:

- `issueLimit`;
- `issuedCount`;
- `remainingIssueCount` (`null` for unlimited campaigns).

Buyer available-campaign responses add `soldOut`. The availability query continues to include active campaigns even when they are sold out. A campaign card shows `선착순 마감` and disables the claim action for a new buyer when `soldOut` is true. A buyer who already owns the campaign coupon sees their issued state in preference to the sold-out call to action.

Store-owner and administrator creation/edit forms accept a blank value for unlimited issuance and display `발급 수 / 한도` plus `잔여 수` on list and detail surfaces. Unlimited values display as `무제한`.

## Persistence And Implementation Boundaries

The migration adds the two campaign columns, validation checks, and supporting indexes only as needed for owner and buyer projections. `CouponCampaign` owns policy validation and exposes explicit methods for draft-only limit changes and persisted issuance-count changes. The repository provides both a conditional count increment for the Redis path and a pessimistic-write campaign lookup for fallback.

The issuing service remains responsible for translating domain outcomes to existing structured errors. Redis connection exceptions are contained at the `CouponIssuanceGate` boundary; they do not leak Redis-specific errors to buyers. The database's campaign/member unique constraint stays the final idempotency guard.

## Verification

- Domain and migration tests cover unlimited campaigns, positive-only limits, draft-only limit edits, count bounds, and existing-campaign migration defaults.
- API tests cover owner visibility, buyer sold-out display data, `409 COUPON_ISSUE_LIMIT_EXCEEDED`, and repeat claims after sellout/end.
- Redis Testcontainers concurrency tests release more simultaneous distinct members than the limit and assert exactly the limit count of committed coupons and `issuedCount`.
- Concurrent retries by one member produce one coupon, one durable count increase, and successful idempotent results.
- A forced database persistence failure releases its Redis reservation so the next eligible member can claim the slot.
- Redis cache reinitialization and simulated Redis connection failure preserve the database limit; the latter exercises the pessimistic-lock fallback.
- Existing wallet, order, payment, settlement, refund, promotion, backend test, and web build checks continue to pass. New JUnit `@Test` methods use Korean underscore-separated names.

## Implementation Sequence

1. Add the campaign migration, domain fields and validation, API DTO/projection fields, and baseline boundary tests.
2. Introduce the issuance-gate abstraction, Redis configuration and Lua scripts, then implement the conditional database confirmation path and reservation compensation.
3. Add the pessimistic-lock fallback and Redis/Testcontainers concurrency and recovery coverage.
4. Update buyer and operator coupon interfaces for limits, remaining counts, and sold-out behavior.
5. Run focused and full backend tests with JDK 21, run the web build, and record the M27 handoff.
