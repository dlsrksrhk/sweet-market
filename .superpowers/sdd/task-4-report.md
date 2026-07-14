# M26 Task 4 Report — Coupon campaign interfaces

## Implemented scope

- Added coupon client contracts, request helpers, and scoped React Query keys in `web/src/features/coupons/couponApi.ts`.
- Added the authenticated buyer coupon wallet at `/me/coupons`:
  - Available campaigns and wallet results load independently.
  - Claiming disables the active request, displays the returned issuance result, and invalidates coupon queries.
  - Wallet views filter the server-returned `ISSUED`, `USED`, `EXPIRED`, and `UNAVAILABLE` statuses; unavailable coupons show the server-returned reason.
- Added the store-owner workspace at `/me/store/coupons` and campaign editor at `/me/store/coupons/:storeId/:campaignId`.
  - Both enforce the active BUSINESS + OWNER store rule in the UI and show the existing access-guidance pattern otherwise.
  - Target selection reuses the owner catalog query.
  - Forms show maximum discount only for percentage discounts and exactly one validity field at a time.
- Added the administrator platform campaign page at `/admin/coupons`.
  - It provides cross-store product search, create/edit support, and lifecycle actions in the existing operational-table visual language.
- Added Shell, My Store, route guards, and responsive coupon metadata styling.

## Verification

Command run from `web`:

```powershell
npm run build
```

Result: exit code 0. TypeScript checks and Vite production build completed successfully. Vite emitted its pre-existing-size style warning for the single JavaScript chunk (517.62 kB); this is a warning, not a build failure.

## Contract note

The backend wallet response currently returns the raw discount fields and status but does not return the brief's illustrative `source`, `storeName`, or `discountText` fields. The UI does not derive eligibility, coupon status, or discount outcome: it renders server status/reason and formats the server's raw monetary/percentage fields for display. Adding origin/store labels would require an API response extension outside Task 4's web-only scope.

## Preserved scope

Checkout and order interfaces were not changed. The pre-existing untracked `web/m26-baseline-npm-install.log` was not modified or included in the commit.

## Review follow-up

- Wallet status is now sent as an API query parameter and enforced by the backend query before paging, including the total count.
- Platform-coupon editing now fetches product choices, preserves the campaign's loaded target products in the checkbox list, and supports searching for additional targets.
- Only the coupon campaign whose claim request is in flight shows the pending state and is disabled.

## Follow-up verification

- `npm run build` completed successfully on 2026-07-14.
- `backend\gradlew.bat test --tests com.sweet.market.coupon.CouponWalletApiTest` completed successfully on 2026-07-14.
