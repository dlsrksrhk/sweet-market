# Milestone 21 Store Foundation And Business Seller Governance Design

## Purpose

Milestone 21 moves Sweet Market from member-owned listings to store-owned commerce. Every ordinary member has one personal store, and a member may own one separately approved business store. Products belong to a store; members operate products only through an active store membership.

This milestone establishes the ownership, authorization, and public identity required by later storefront, inventory, catalog, promotion, and coupon work. It does not implement a full store catalog or operator console.

## Confirmed Decisions

- Keep a personal store and a business store as separate stores. A business application does not convert or overwrite the personal store.
- Product creation requires the operator to select an active store explicitly.
- Model `OWNER` and `MANAGER` roles now, but defer manager invitation and assignment UI to Milestone 22. A newly approved business store initially has only its applicant as `OWNER`.
- Exclude pending, rejected, and suspended business stores from normal buyer catalog results. A direct product URL remains readable as an unavailable product and cannot be purchased.
- A rejected business application is corrected and resubmitted through the same store, which returns to `PENDING_VERIFICATION`.
- Buyer surfaces show a personal store name for personal sellers and the public brand name for business stores. Business registration data and operator identities are never buyer-facing.
- Use a versioned database migration for existing-member personal-store backfill. Create a personal store in the member-registration transaction for every new ordinary member.
- Changing a business legal name or registration identifier requires re-verification. Changing the public brand name or introduction applies immediately.
- Limit a member to one personal store and one business store they own in this milestone.
- Make `Store` the only commercial owner of a product. Do not retain both member and store as independent mutable product owners.

## Domain Model

### Store

`Store` owns public commerce identity, lifecycle, profile data, and business-verification fields.

| Field or concept | Rule |
| --- | --- |
| Public identifier | Stable store id returned by APIs and used in routes. |
| Type | `PERSONAL` or `BUSINESS`. |
| Public profile | Store name and introduction. A personal store name initially uses the owner nickname. A business store uses its brand name. |
| Business data | Legal business name and registration identifier are operational data and not public response fields. |
| Status | Personal: `ACTIVE`, `SUSPENDED`. Business: `PENDING_VERIFICATION`, `ACTIVE`, `REJECTED`, `SUSPENDED`. |
| Review data | Rejection reason is visible only to the owner and administrator. |

Business stores move through the following transitions:

```text
application:        -> PENDING_VERIFICATION
approve:            PENDING_VERIFICATION -> ACTIVE
reject:             PENDING_VERIFICATION -> REJECTED
resubmit:           REJECTED -> PENDING_VERIFICATION
suspend:            ACTIVE -> SUSPENDED
reactivate:         SUSPENDED -> ACTIVE
legal-data change:  ACTIVE -> PENDING_VERIFICATION
```

Only an administrator approves, rejects, suspends, or reactivates a business store. A personal-store owner or business-store owner updates public profile data. Only a business-store owner may submit or resubmit business verification data.

### StoreMembership

`StoreMembership` is the database-backed member-to-store authorization boundary.

- Roles are `OWNER` and `MANAGER`.
- A store operation requires an active membership; the client route does not grant access.
- `OWNER` manages profile and sensitive business data. `MANAGER` may perform store catalog and ordinary operational commands, but cannot change ownership or sensitive business data.
- M21 creates the owner membership for every personal-store backfill and business application. It does not provide manager invitation or assignment workflows.

### Product Ownership

`Product` changes from a `Member seller` relation to a required `Store store` relation.

- Existing products migrate to the current seller's personal store.
- Product creation accepts a selected `storeId` and requires an active `OWNER` or `MANAGER` membership. A business store must be `ACTIVE` before it can publish products.
- Product update and hide require the same active catalog-operation roles in the product's existing store. They cannot transfer a product between stores.
- Existing orders, payments, settlements, refunds, and reviews retain their historical member seller relationships. Those aggregates are not rewritten in M21.

## Migration Strategy

Use versioned database migrations in this order:

1. Create store and membership tables with indexes and constraints.
2. Backfill one personal store and one active owner membership for each existing ordinary member. The migration must be safe against duplicates.
3. Add `products.store_id`, populate it from each product seller's personal store, then make it non-null.
4. Update application mappings and queries to use the store relation.
5. Remove the obsolete direct product seller relation only after the migrated relation is verified.

New ordinary-member registration persists the member, personal store, and owner membership in one transaction. Database constraints enforce one personal store per owner and one owned business store per owner in this milestone.

## API And Read-Model Contracts

### Public Product And Store Data

- Product summary and detail responses add `storeId`, `storeName`, and `storeType`.
- Existing `sellerId` and `sellerNickname` fields remain temporarily for callers that depend on them. They are derived from the store owner membership and must never identify a member inconsistent with the product store.
- A minimal public store profile endpoint supports a route such as `/stores/:storeId`. It exposes only store identity, type or verification signal, and introduction. Milestone 22 expands it into a paginated storefront.
- Public reads exclude non-active business stores from normal listings. Direct product reads return a non-purchasable unavailable state when a business store is pending, rejected, or suspended.

### Store Operator Commands

- My Store read and profile update endpoints return the member's personal store and, when present, their business store.
- A business application command creates the member's one business store in `PENDING_VERIFICATION` status and creates its owner membership.
- A rejected business-store owner can correct application data and resubmit it.
- Product create accepts the active selected store. Existing product update and hide operations resolve access through store membership.

### Administrator Commands

- An administrator can list and inspect business-store applications with status filters.
- Administrator commands approve, reject with a required reason, suspend, and reactivate according to the defined transitions.
- Administrator and owner views may show legal business data. Public responses must not.

## Web Experience

- Product cards and detail pages show the responsible store's name, type, and business verification signal where applicable. The store name links to the minimal public profile route.
- My Store separates public profile editing from business verification information. It explains pending, rejected, suspended, and active states in concise operational language.
- The product registration form lists only stores the member can currently operate. Personal stores and active business stores are selectable; inactive business stores are disabled with their reason.
- The business application screen supports first submission and rejected-application correction without creating a duplicate store.
- The administrator surface is a table-and-detail workflow with status, applicant and store identifiers, timestamps, private review data, a rejection-reason input, and explicit state-transition actions.

## Error Handling And Access Rules

- A member without active membership receives an authorization error for store-owned commands.
- A pending, rejected, or suspended business store cannot create, update, hide, or publish products.
- A member who already owns a business store cannot create a second application.
- Invalid state transitions, missing rejection reasons, and invalid business-field formats return validation errors.
- Purchase and cart flows revalidate product availability so an unavailable business-store product cannot be ordered through a stale product page.

## Query, JPA, And Database Design

- Keep `Store` and `StoreMembership` lazy from `Product`; catalog and product read models use explicit projections or bounded joins for the exact store fields required.
- Add indexes for membership authorization lookups by member and store, public store lookups, and product reads by store and product status according to final query plans.
- Do not fetch a to-many membership collection for each product row. Product summary queries select only the public store identity needed by the card.
- Verify that adding store identity does not add an N+1 store or membership lookup to product, order, refund, settlement, or review reads.

## Verification And Exit Criteria

- Tests cover new-member personal-store creation, existing-member backfill behavior, and duplicate prevention.
- Tests cover business application, approval, rejection, resubmission, suspension, reactivation, legal-data re-verification, and invalid transitions.
- Tests cover owner, manager, outsider, and administrator authorization boundaries.
- Existing products resolve to exactly one migrated personal store, while product/order/refund/settlement/review behavior remains compatible.
- Public responses consistently represent store and temporary seller fields and never leak registration data, review reasons, or memberships.
- Tests or SQL inspection show bounded query behavior for representative product and operator reads.
- The backend full suite, web build, `git diff --check`, and a manual buyer/operator/administrator flow pass.

## Out Of Scope

- Manager invitation, assignment, removal, and multi-store organization workflows.
- Full buyer storefront catalog, store operator catalog console, bulk product actions, and store dashboard work; these belong to Milestone 22.
- Stock-managed inventory, inventory adjustments, and high-contention reservations; these belong to Milestones 23 and 29.
- Global catalog discovery, keyset search, promotions, coupons, performance caching, and dashboards.
- Subscription plans, fees, KYC integration, tax invoices, external notification delivery, and legal accounting integrations.

## Handoff To Milestone 22

Milestone 22 expands the minimal public store profile into a storefront and adds the catalog-management console. It must reuse `Store`, `StoreMembership`, store-scoped product authorization, and the product-to-store ownership relation introduced here rather than creating another seller-profile model.
