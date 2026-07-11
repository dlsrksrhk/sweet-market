# Milestone 21 Task 5 Store Governance Web Design

## Purpose

Task 5 exposes the store ownership and governance capabilities completed in the backend through focused owner, buyer, and administrator web flows. It connects products to public store identity without expanding into the full storefront and catalog console planned for Milestone 22.

## Scope

This task includes:

- My Store profile and lifecycle status UI for one personal store and at most one business store.
- A separate business application and rejected-application resubmission page.
- A minimal public store profile.
- An administrator business-store list/detail review surface.
- Product card, detail, create, and edit integration with store identity.
- A backend business-store status filter required for correct administrator pagination.

This task excludes manager invitation/assignment UI, a public store catalog, store dashboards, bulk catalog operations, promotions, inventory, and the Milestone 22 operator console.

## Confirmed Decisions

- `/me/store` switches between personal and business stores on one page.
- Business application and resubmission use a separate authenticated route.
- Administrator review uses one list-plus-detail screen.
- Product creation uses status-aware store selection cards rather than a compact select.
- Task 5 owner UI uses owned store ids from `/api/stores/me`; manager web entry remains deferred.
- Administrator status filtering is performed by the backend, not against one client-side page.
- The implementation follows a shared store feature module plus role-specific pages.

## Architecture And Routes

`web/src/features/stores/storeApi.ts` is the single boundary for store API contracts and functions. Pages consume that module and own only page-specific selection, form, and mutation state.

Routes:

- `/me/store` — authenticated personal/business store settings and status.
- `/me/store/business-application` — authenticated first application or rejected-store resubmission.
- `/stores/:storeId` — public minimal store profile.
- `/admin/business-stores` — administrator status-filtered list and detail review.

The shell adds My Store navigation for authenticated members and business-store review navigation for administrators. Existing auth and admin route guards remain the authorization boundary for page entry; the backend remains authoritative for every command.

React Query keys are role-scoped:

- `['stores', 'me']`
- `['stores', 'public', storeId]`
- `['admin', 'business-stores', filters]`
- `['admin', 'business-store', storeId]`

## My Store

My Store selects the personal store by default. If a business store exists, a compact switcher changes the active settings panel without changing routes. If no business store exists, the page shows a call to action to the separate application route.

The selected store panel shows public name, introduction, type, status, and a public-profile link. The owner can update public name and introduction. Business state guidance is explicit:

- `PENDING`: verification is in progress and catalog operations are unavailable.
- `REJECTED`: show the private rejection reason and a resubmission link.
- `ACTIVE`: show the verified signal and public-profile link.
- `SUSPENDED`: explain that catalog operations are unavailable.

Legal business data is not mixed into the public-profile edit form.

## Business Application

The separate application page handles two cases with one form:

- No business store: submit with `POST /api/stores/business-applications`.
- Rejected business store: prefill existing private data and submit with `PATCH /api/stores/business-applications/{storeId}`.

Pending, active, or suspended stores do not receive an editable application form. Direct access shows the current state and a link back to My Store. The page explains that legal business name and registration identifier are private operational data.

## Public Store Profile

The public page shows only public name, personal/business type or verified signal, and introduction. It never renders legal business name, registration identifier, rejection reason, memberships, or operator identity.

Task 5 does not add a product catalog to this page. Milestone 22 expands the minimal profile into a storefront.

## Administrator Review

The administrator page follows the existing operations-screen pattern: a status-filtered list with a selected detail panel. The status filter is reflected in request state and resets pagination when changed.

The detail panel shows store id, owner/applicant identifiers, legal business data, public profile, lifecycle status, rejection reason, and timestamps. It displays only valid actions for the current state:

- Pending: approve or reject.
- Active: suspend.
- Suspended: reactivate.
- Rejected: read-only until the owner resubmits.

Reject requires a nonblank reason before sending the command. Successful commands invalidate the filtered list, selected detail, My Store, and public profile queries relevant to the store.

The backend adds an optional `StoreStatus status` filter to `GET /api/admin/business-stores`. No status returns all business stores; a status returns a correctly paginated server-side result.

## Product Integration

Product summary and detail TypeScript contracts add `storeId`, `storeName`, `storeType`, and direct-detail `purchasable`, while retaining temporary `sellerId` and `sellerNickname` compatibility fields.

Product cards and detail pages link the store name to `/stores/{storeId}` and display a compact personal/business label.

Create mode loads owned stores and renders selection cards:

- Active personal and business stores are selectable.
- Inactive business stores remain visible with their status and disabled reason.
- One selectable store is selected by default.
- Multiple selectable stores require an explicit user choice.
- No selectable store disables submission and links to My Store.

The create payload includes the selected `storeId`. Edit mode displays the product's existing store as read-only and never offers a transfer operation.

The current `member.id === product.sellerId` edit check is removed. Task 5 shows edit controls when the product `storeId` belongs to the owned-store ids returned by `/api/stores/me`. Manager-facing web access remains deferred until membership management exists, while backend manager authorization remains unchanged.

## Loading, Errors, And Mutation State

Every page distinguishes loading, empty, error, and mutation-pending states. Mutating controls are disabled while a request is pending to prevent duplicate submissions and state transitions.

API error rendering uses the first `ApiError.fieldErrors` message, then the server message, then a screen-specific fallback. A successful mutation clears stale errors and invalidates only its relevant query keys.

Invalid or missing route ids render the existing error-state component. The UI does not infer authorization from route visibility or cached state; backend errors remain authoritative.

## Verification

- Store API TypeScript contracts match backend response and request records.
- My Store switches between personal and business stores and shows correct status actions.
- Business application chooses POST versus PATCH correctly and blocks other lifecycle states.
- Public profile never renders private business or membership data.
- Administrator server-side status filtering and pagination are covered by backend tests.
- Administrator actions, required rejection reason, and query invalidation are covered.
- Product creation sends `storeId`; edit mode keeps the existing store.
- Product cards/details link consistent store identity.
- Edit controls use owned store ids rather than legacy seller equality.
- Loading, empty, error, and pending states are explicit.
- Relevant backend tests, `npm run build`, and `git diff --check` pass.

## Handoff

After Task 5, Task 6 runs the full backend and web verification gate and records the final Milestone 21 handoff. Milestone 22 reuses these routes, contracts, store identity, and authorization rules when expanding the public profile and owner settings into full storefront and operator-console experiences.
