# Milestone 26: Coupon Campaigns And Standard Coupon Issuance Design

## Purpose

Platform administrators and active business-store owners can operate reusable coupon campaigns. Buyers discover claimable campaigns and manage their issued coupons in one wallet. This milestone establishes campaign policy, issuance idempotency, ownership-scoped operations, and coupon history; it does not apply coupons to orders.

## Scope

- Support platform-owned and store-owned coupon campaigns.
- Permit either all-product or selected-product applicability.
- Permit platform campaigns to select products from multiple stores.
- Restrict store campaigns and their selected products to the owning store.
- Support fixed-won and percentage discounts, percentage maximum discount, minimum purchase amount, and a persisted `stackable` policy.
- Support an issue window and either a shared expiry instant or a per-issue validity-days policy.
- Let each authenticated buyer claim each campaign at most once, returning a stable result for repeated requests.
- Provide buyer campaign discovery and a paged coupon wallet at `/me/coupons`.
- Provide active business-store owner and platform administrator campaign-management surfaces.

## Explicitly Out Of Scope

- Limited issuance, first-come events, reservation counters, or queueing.
- Coupon selection or redemption while creating an order.
- Applying a coupon to payments, settlements, refunds, reports, carts, or promotion-price calculations.
- Multi-coupon calculation, exclusive-coupon behavior, and allocation across multiple cart items.
- Personal-store campaigns, segmentation, CRM, notifications, or bulk coupon grants.

## Ownership And Authorization

`CouponCampaign` has an immutable owner selected at creation:

- `PLATFORM` campaigns have no store and can be created or operated only by an administrator.
- `STORE` campaigns reference exactly one store and can be created or operated only by an `OWNER` of an active `BUSINESS` store.

The database relation and every service query enforce the owner scope; client route guards are convenience only. Store managers, personal-store owners, unrelated members, and inactive business stores cannot manage store campaigns. A platform operator never impersonates a store owner.

## Campaign Model

`CouponCampaign` owns policy and lifecycle:

- owner type and nullable store reference;
- applicability scope: `ALL_PRODUCTS` or `SELECTED_PRODUCTS`;
- selected product targets when the scope is selected;
- discount type (`FIXED_AMOUNT` or `PERCENTAGE`), non-negative discount value, and nullable percentage maximum discount amount;
- non-negative minimum purchase amount and `stackable` flag;
- buyer-facing title and optional label;
- issue start/end instants, stored in UTC;
- validity type: `COMMON_EXPIRY` or `DAYS_FROM_ISSUANCE`;
- either one common expiry instant or a positive validity-days value;
- persisted lifecycle state: `DRAFT`, `SCHEDULED`, `PAUSED`, or `ENDED`.

The operator enters dates in `Asia/Seoul`; requests convert these to `Instant` and responses convert them back to KST. A `Clock` supplies the current time.

Lifecycle follows the existing promotion convention. A `SCHEDULED` campaign is effectively `ACTIVE` only inside its issue window. At and after the issue end instant, it is effectively `ENDED` without a background write. Paused and ended campaigns are never claimable; an ended campaign cannot resume. Draft and not-yet-started scheduled campaigns are editable. Once effectively active, policy and targets are immutable; only pause or end is permitted. The campaign owner is never transferred.

`CouponCampaignTarget` links a selected-scope campaign to one product. A unique database constraint prevents duplicate targets. A store campaign target must belong to that campaign's store. A platform campaign may target eligible products belonging to any number of stores. New targets must be buyer-purchasable at update time; a later product status change does not delete history but prevents future use when redemption is introduced.

Validation requires an issue start earlier than its issue end. For `COMMON_EXPIRY`, the common expiry must be at or after the issue end so a buyer cannot claim an already expired coupon. For `DAYS_FROM_ISSUANCE`, validity days must be at least one. Percentage maximum discount is required only for percentage campaigns and is non-negative; fixed campaigns reject it. Selected scope requires a non-empty, deduplicated target set; all-product scope has no target rows.

## Issued Coupon Model And Validity

`MemberCoupon` represents one issued coupon owned by one member. It references its campaign and persists:

- member and campaign IDs;
- issue timestamp and the concrete `validUntil` instant;
- issued discount type/value, percentage maximum discount, minimum purchase amount, applicability scope, target policy identity, and stackable flag;
- lifecycle state initially `ISSUED`, with `USED` reserved for M28 redemption;
- optional future order/redemption reference, not introduced by this milestone.

At claim time, common-expiry campaigns copy their common expiry into `validUntil`; validity-days campaigns calculate `issuedAt + validityDays`. The issue-time policy snapshot prevents a later draft/scheduled campaign edit from rewriting issued coupon terms. Campaign state remains a live gate: pausing or ending a campaign immediately makes all of its previously issued coupons unavailable, even when their `validUntil` has not passed. The issued row is retained for audit and wallet history.

Wallet responses expose a derived status with this priority:

1. `USED` for an eventually redeemed coupon;
2. `EXPIRED` when `validUntil` is not in the future;
3. `UNAVAILABLE` when its campaign is paused, ended, or otherwise not currently usable;
4. `ISSUED` for a coupon that is still usable.

M26 creates only `ISSUED` coupons. The `USED` state is included in the read contract and schema so M28 can consume it without replacing the wallet model.

## Issuance And Idempotency

The buyer claims with `POST /api/coupon-campaigns/{campaignId}/claim`. In one transaction, `CouponIssueService` verifies the authenticated member, campaign existence, effective active state, issue window, and prior issuance. It creates a `MemberCoupon` with an issue-time snapshot, then returns it.

The database enforces `unique (coupon_campaign_id, member_id)`. If a repeat request sees an existing row, the service returns that row as a successful stable idempotent result. If concurrent requests race into the unique constraint, the losing transaction catches only that expected duplicate-key outcome, re-reads the committed row, and returns the same result. Other persistence failures remain errors.

No claim reserves inventory or changes an order. Issuance eligibility does not require a buyer to own a target product; product scope determines where a future redemption can be used, not who may claim the campaign.

## API Boundaries

Buyer APIs:

```text
GET  /api/coupon-campaigns/available?page=&size=&source=&storeId=
POST /api/coupon-campaigns/{campaignId}/claim
GET  /api/me/coupons?page=&size=&status=
```

The available-campaign query returns only currently claimable campaigns and includes a buyer-specific `claimed` flag. It uses a paged projection and does not load all issued coupons or targets per row. The wallet returns the issued snapshot terms, derived status, `validUntil`, campaign title/source, applicable store information when present, and a safe buyer-facing unavailability reason.

Store owner APIs reuse the M25 location and access style:

```text
GET   /api/stores/{storeId}/coupon-campaigns
POST  /api/stores/{storeId}/coupon-campaigns
GET   /api/stores/{storeId}/coupon-campaigns/{campaignId}
PATCH /api/stores/{storeId}/coupon-campaigns/{campaignId}
POST  /api/stores/{storeId}/coupon-campaigns/{campaignId}/schedule
POST  /api/stores/{storeId}/coupon-campaigns/{campaignId}/pause
POST  /api/stores/{storeId}/coupon-campaigns/{campaignId}/resume
POST  /api/stores/{storeId}/coupon-campaigns/{campaignId}/end
```

Administrator endpoints use the same operations under `/api/admin/coupon-campaigns`. Lists are always paged and filterable by lifecycle/effective status and bounded period values. Error responses use existing structured validation, access, not-found, and conflict conventions; a duplicate claim remains a success, not a conflict.

## Web Experience

`/me/coupons` contains a claimable-campaign area and a wallet area. Campaign cards display source (`플랫폼` or store name), product scope, discount and maximum-discount terms, minimum purchase amount, expiry policy, and a direct claim action. The action has a pending state and reports successful issue or the already-issued result explicitly.

The wallet uses compact status filters for usable, used, expired, and unavailable coupons. A coupon card clearly explains whether it became unavailable due to campaign pause/end or expired by its own `validUntil`. It preserves the issued policy details instead of silently showing the campaign's current mutable draft policy.

Store operators access coupon campaigns from the existing My Store workspace. Only active business-store owners see functional management controls; other store roles receive a clear access explanation. Administrators use a table-oriented campaign list and lifecycle page aligned with the existing admin operations UI. Both owner surfaces support selected-product targets, while platform campaign editing allows products from multiple stores.

## Persistence And Query Shape

The migration adds campaign, target, and member-coupon tables plus foreign keys and constraints. Required constraints include unique campaign-target pairs and unique campaign-member issuance pairs. Candidate indexes include:

- campaign owner/lifecycle/issue-period lookup;
- target product/campaign lookup;
- member-coupon wallet lookup by member, persisted state, and `valid_until` with deterministic ID ordering;
- campaign-member unique index for idempotent issuance.

Wallet and management lists use explicit paged projections. They must query by member ID or owner scope in SQL and must not hydrate nested campaign targets or every member coupon. The available-campaign query may use a bounded left join or existence predicate for the acting member's claimed flag, never one claim lookup per card.

## Verification

- Domain and API tests cover both owners, active-business-store restrictions, manager/outsider denial, selected-target ownership, and platform targets across stores.
- Tests cover fixed and percentage terms, percentage caps, minimum purchase amount, both validity policies, KST issue-window boundaries, lifecycle transitions, and immutable issued snapshots.
- Sequential and concurrent-style repeated claims produce exactly one `MemberCoupon` and the same stable response.
- Wallet tests cover member isolation, pagination, every derived status, and immediate unavailability after campaign pause/end.
- Query tests protect paged wallet/available/owner lists from N+1 target or issued-coupon reads.
- Existing order, payment, settlement, refund, promotion, and web build checks continue to pass; no M26 code changes order pricing or checkout behavior.

## Implementation Sequence

1. Add the migration, campaign/target/member-coupon aggregates, lifecycle, and validity-policy tests.
2. Add store-owner and administrator campaign-management APIs with ownership and target validation.
3. Add transaction-safe idempotent claim and buyer available-campaign/wallet query APIs.
4. Add store/admin campaign surfaces and the buyer coupon page.
5. Run focused query and API tests, full compatibility verification, web build, and document the handoff.
