# Milestone 25: Store Promotions And Price Policy Design

## Purpose

Business-store owners can create automatic, time-bounded promotions for selected products or their whole store. Buyers see a consistent list price, promotion discount, and effective price wherever the product is discovered or purchased. The server is the authority for every price calculation and preserves the price actually used by each order.

## Scope

- Support promotions owned by an `ACTIVE` `BUSINESS` store only.
- Allow only the store `OWNER` to create, update, schedule, pause, resume, or end promotions.
- Support `SELECTED_PRODUCTS` and `STORE_WIDE` targeting.
- Support fixed-won and percentage discounts.
- Show effective promotion pricing in catalog, storefront, product detail, cart, and order views.
- Filter and sort catalog results by effective price.
- Revalidate the promotion at order creation and persist a price snapshot.

## Explicitly Out Of Scope

- Personal-store promotions.
- Platform-wide promotions.
- Stacking more than one promotion.
- Coupons, coupon redemption, and coupon price calculation.
- Multi-product or multi-store discount allocation.
- Price guarantees based on the time an item entered the cart.

## Domain Model

### PromotionCampaign

`PromotionCampaign` is a store-owned aggregate with:

- store id;
- target scope: `SELECTED_PRODUCTS` or `STORE_WIDE`;
- discount type: fixed amount or percentage;
- discount value;
- priority;
- buyer-facing title and optional short label;
- start and end instants, stored in UTC;
- persisted lifecycle state: `DRAFT`, `SCHEDULED`, `PAUSED`, or `ENDED`.

The operator enters and sees dates in `Asia/Seoul`. Requests convert the supplied KST date-time to an `Instant`; responses convert it back to KST for the web UI. A `Clock` supplies the current time.

`DRAFT` is editable but has no buyer effect. Scheduling changes it to `SCHEDULED`. A scheduled campaign is effectively `ACTIVE` when the current instant is within its inclusive start and exclusive end window. Its effective status becomes `ENDED` after the end instant without a background write. Pausing and ending are manual, immediate, and irreversible for `ENDED`. Resuming a paused campaign is allowed only while its end instant is still in the future; it returns to `SCHEDULED` and is immediately effective if its start instant has already passed.

`DRAFT` campaigns and `SCHEDULED` campaigns whose start instant is still in the future allow edits to their target scope, targets, period, discount, priority, title, and label. Once a scheduled campaign is effectively `ACTIVE`, it may only be paused or ended.

### PromotionTarget

`PromotionTarget` links one selected-product campaign to a product. A database uniqueness constraint prevents duplicate product links inside one campaign. `STORE_WIDE` campaigns have no target rows. Every target product must belong to the campaign store and must be buyer-purchasable when added; hidden, reserved, sold-out, or zero-available stock-managed products cannot be newly targeted. A later product state change does not remove the relation but makes the product ineligible for buyer pricing.

## Effective Price Policy

For every buyer-facing price calculation:

1. Start from the product's immutable-for-the-calculation `listPrice` (`Product.price`).
2. Find active campaigns from the product's active business store whose scope is `STORE_WIDE` or whose selected targets include the product.
3. Calculate each candidate's discount in whole Korean won. Percentage discounts round down.
4. Clamp a candidate effective price to zero or above.
5. Choose exactly one candidate with the lowest effective price.
6. Break an equal-effective-price tie by higher campaign priority, then lower campaign id.

The result is a `PromotionPrice` value containing `listPrice`, nullable campaign id/title, `promotionDiscountAmount`, and `effectivePrice`. No promotion returns the list price as the effective price, a zero discount, and null campaign fields.

Promotion selection never stacks discounts. The buyer cannot submit a price or a promotion amount as an authority.

## Persistence And Order Snapshot

A Flyway migration adds a promotion campaign table, a promotion target table, their foreign keys, and query-shape-driven indexes for campaign activity and target lookup.

The same migration adds non-null monetary snapshots and a nullable campaign reference to `orders`:

```text
list_price
promotion_campaign_id nullable
promotion_discount_amount
final_price
```

Existing orders backfill from their product price at migration time with a zero promotion discount and a null campaign id. New-order creation obtains a current `PromotionPrice` immediately before creating the order, persists its fields, and uses `finalPrice` for payment, settlement, refund, reporting, and response projections. A promotion ending or changing after order creation cannot alter historical records.

## Query And API Boundaries

### Pricing Services

`PromotionPricingService` owns the authoritative campaign eligibility and price calculation for product detail, cart, and the order mutation boundary. It receives an explicit current time from `Clock` and returns `PromotionPrice`.

`CatalogSearchRepository` extends its existing native JDBC projection with an effective-promotion selection that returns the same price fields. It must calculate candidate discounts and apply the same tie-break rule in SQL because catalog `minPrice`, `maxPrice`, `PRICE_ASC`, `PRICE_DESC`, and their keyset cursors use `effectivePrice`. The query remains a card projection and must not load promotion target collections or execute a promotion query per card.

### Owner APIs

All endpoints below first validate that the actor is the `OWNER` of an active business store.

```text
GET   /api/stores/{storeId}/promotions
POST  /api/stores/{storeId}/promotions
GET   /api/stores/{storeId}/promotions/{promotionId}
PATCH /api/stores/{storeId}/promotions/{promotionId}
POST  /api/stores/{storeId}/promotions/{promotionId}/schedule
POST  /api/stores/{storeId}/promotions/{promotionId}/pause
POST  /api/stores/{storeId}/promotions/{promotionId}/resume
POST  /api/stores/{storeId}/promotions/{promotionId}/end
```

The list endpoint is paginated and supports bounded status and period filters. Campaign responses expose configured state and derived effective status, KST period values, target scope/count, discount rule, priority, and buyer-facing text. The detail endpoint includes selected target products only for `SELECTED_PRODUCTS` campaigns.

### Buyer Responses

Existing catalog cards, storefront cards, product detail, cart rows, order responses, payment reads, settlement reads, refund reads, and reports receive a consistent price representation:

```text
listPrice
promotionId nullable
promotionTitle nullable
promotionDiscountAmount
effectivePrice
```

The cart always returns the current effective price. At order creation, the service recalculates the price; a promotion that has ended since the cart was read is not applied.

## Web Experience

`/me/store/promotions` is the dedicated business-owner workspace. Its list shows campaign status, KST period, target scope, target count, discount summary, and primary lifecycle action. It offers filtering and navigation to create/detail views.

The create/edit form supports selecting either scope, choosing products only for `SELECTED_PRODUCTS`, entering period and discount data in KST, and showing validation failures near their fields. Active campaigns show their price policy and targets but expose only pause/end actions. Draft and scheduled campaigns expose full editing controls.

Buyer product cards use a restrained price block: display a struck-through list price only when an effective promotion exists, show effective price prominently, and show the campaign title where space permits. Product detail and cart make the promotion amount and current-price nature clear. Loading states reserve price space so image grids do not shift.

## Validation And Errors

- Reject a non-business or inactive store and every non-owner actor.
- Reject start times at or after end times and negative discount values. Effective prices are clamped to zero so no buyer or order price becomes negative.
- Reject target products from another store and products not currently buyer-purchasable.
- Reject lifecycle transitions not allowed by the persisted state or current time; an ended campaign never resumes.
- Return structured validation, access, and conflict responses using the existing `ErrorCode` and `BusinessException` conventions. Add promotion-specific codes only where callers need a distinct recovery path.

## Verification

- Domain and integration tests cover owner, manager, outsider, personal-store, inactive-store, and business-store authorization boundaries.
- Tests cover both scopes, target validation, fixed and percentage discounts, KST schedule boundaries, pause/resume/end behavior, and edit restrictions.
- Tests prove lowest-effective-price selection and deterministic priority/id tie breaking.
- Catalog integration tests cover effective-price filters, sorts, keyset page boundaries, and no duplicates/skips.
- Catalog SQL/query-budget tests prove no promotion N+1 behavior and no collection-fetch regression.
- Tests cover cart price refresh, promotion expiry before order creation, snapshot backfill, and immutable final price through payment, settlement, refund, and reporting projections.
- The backend suite runs on JDK 21 with `JWT_SECRET`; new JUnit test method names use Korean with underscores.
- Web production build and `git diff --check` pass.

## Implementation Sequence

1. Introduce migrations and focused promotion domain/lifecycle tests.
2. Add owner authorization and campaign/target management APIs with integration tests.
3. Implement authoritative price calculation and price snapshots across order/payment/read projections.
4. Extend catalog SQL, filters, sorting, cursors, and query-budget tests for effective prices.
5. Extend buyer price rendering and add the owner promotions workspace.
6. Run focused and full verification, then document the handoff.
