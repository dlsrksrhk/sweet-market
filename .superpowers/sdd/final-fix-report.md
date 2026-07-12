# Milestone 23 Final Fix Report

## Commit

- Implementation: `0adc7d35e5f4e2a39e6eccac107d4da287cf60e7` (`fix: close inventory final review gaps`)
- Documentation: the commit containing this report and the updated milestone handoff.

## Corrections

- Payment approval now has a non-transactional coordinator and an isolated transactional attempt. On `IllegalStateException`, a `REQUIRES_NEW` cleanup locks a still-created stock-managed order, cancels it, releases the reservation, restores availability, and persists one `RELEASE` audit. Audit guards make a repeated failure idempotent. Single-item and already-paid flows are not cleaned up.
- Wishlist add obtains the existing buyer-safe availability projection and rejects zero stock with `WISHLIST_PRODUCT_NOT_ON_SALE`. Wishlist list joins only `Inventory`, computes the catalog status, returns `BuyerAvailabilityResponse`, reveals quantity only for `LOW_STOCK`, and renders `BuyerAvailabilityBadge` in the web UI.
- Inventory history adds nullable `orderId`, tests reservation/release/shipment commitment mappings, and renders related order numbers in store operations.

## Exact verification

Backend command:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE='4'
.\gradlew.bat test --tests 'com.sweet.market.payment.PaymentApiTest' --tests 'com.sweet.market.wishlist.WishlistApiTest' --tests 'com.sweet.market.store.StoreOperationsApiTest' --tests 'com.sweet.market.inventory.*' --tests 'com.sweet.market.order.OrderApiTest' --rerun-tasks
```

Result: exit 0, `BUILD SUCCESSFUL in 51s`; 79 tests in 8 suites, 0 failures, 0 errors, 0 skipped.

- `PaymentApiTest`: 10
- `WishlistApiTest`: 18
- `StoreOperationsApiTest`: 28
- Inventory application/domain suites: 12
- `OrderApiTest`: 11

Web command: `cd web; npm run build`

Result: exit 0; TypeScript checks passed, Vite 6.4.3 transformed 138 modules and built in 1.65s.

Hygiene: `git diff --check` returned exit 0 with no diagnostics before the implementation commit. Root `package-lock.json` stayed untracked and unstaged.

## Concerns

- This final-review pass did not rerun the full backend suite; it deliberately claims only the relevant 79-test selection above.
- No authenticated browser walkthrough or live 390px viewport measurement was available. The web production build is the UI verification evidence.
