# Task 7 Report: Promotion price compatibility and release verification

## Delivered

- Added payment, settlement, and refund regressions for orders created under a promotion and read after the campaign ends and the current product price changes.
- Verified payment approval, buyer order reads, settlement creation/listing, and refund-request flow retain order snapshot prices.
- Captured PostgreSQL 17 effective-price query-plan evidence with the intended catalog, campaign, and promotion-target indexes.
- Wrote the Milestone 25 handoff at `docs/superpowers/handoffs/2026-07-14-milestone-25-store-promotions-and-price-policy-handoff.md`.

## Focused verification

`PaymentApiTest`, `SettlementApiTest`, and `RefundRequestApiTest` passed with JDK 21, `JWT_SECRET`, and Hikari pool size 4: 50 tests total, zero failures/skips.

## Release verification

- Promotion/catalog/product/cart/order/payment/settlement/refund focused compatibility suite: passed.
- Full backend: `gradlew.bat test --rerun-tasks` with JDK 21, `JWT_SECRET`, and Hikari pool size 4 — passed (572 tests, 0 failures/errors/skips).
- Web: `npm run build` — passed after Task 6 commit.
- `git diff --check` — passed.
