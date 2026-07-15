# Milestone 26 coupon campaign release handoff

## Final review corrections

- Coupon scope is consistently `ALL_PRODUCTS` in the coupon API and React client; the M25-only `STORE_WIDE` vocabulary remains isolated in promotions.
- `V10__align_coupon_policy_snapshots.sql` replaces the V9 percentage-cap check without editing the applied migration. Percentage campaigns may omit `max_discount_amount`; fixed-amount campaigns still reject it.
- `MemberCoupon` now snapshots selected target product IDs in `member_coupon_target_products` at issue time. Future redemption can therefore use the issued policy even when a draft/scheduled campaign target set is edited later.
- Draft campaigns remain editable until they are scheduled. Their effective badge remains `SCHEDULED`, preventing an unscheduled campaign from appearing claimable after its configured start time.
- Available discovery accepts `source` and `storeId`, and both discovery and wallet projections expose source plus applicable store information. The client contract keys and serializes those filters.
- Owner campaign lists use a grouped `CouponCampaignSummaryRow` projection with a bounded target count; wallet and discovery remain projection reads with no target-collection hydration.

## Focused verification

Executed with JDK 21 (`C:\Users\kdh\.jdks\corretto-21.0.7`), `JWT_SECRET=sweet-market-local-test-secret-key-32bytes-minimum`, and a four-connection Hikari pool:

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.coupon.*' --rerun-tasks
```

Result: `BUILD SUCCESSFUL`. Coverage includes draft editing, issued target snapshots, optional percentage caps, discovery/wallet source fields, migration V10, and owner/discovery/wallet query budgets.

```powershell
cd web
npm run build
```

Result: TypeScript validation and Vite production build completed successfully.

## Deferred scope

- M28 remains responsible for applying the persisted coupon target snapshot during redemption and for coupon stacking/calculation.
- Claims, carts, orders, payments, settlements, and refunds remain coupon-free in M26.
