# M21 Task 5 Final Fix Report

## Status

DONE

## Fixes

- Product detail edit/hide authorization now waits for authentication and the account-scoped owned-store query, reports structured query failures, and uses `ownedStoreIds.has(product.storeId)`.
- The owned-store query cache is cleared at authentication boundaries so another account's cached ownership cannot render controls while fresh data is loading.
- Order creation and new cart/wishlist additions honor `product.purchasable`; existing cart and wishlist entries remain removable, and unavailable-product guidance is visible.
- Store query failures in `MyStorePage`, `BusinessStoreApplicationPage`, `StoreProfilePage`, and `AdminBusinessStoresPage` use the API field-error, server-message, and local-fallback chain.
- Store profile and business application validation messages have stable IDs, `aria-invalid`/`aria-describedby` associations, alert semantics, and live success status semantics.
- `StoreApiTest` now parses both admin-list timestamps as ISO `LocalDateTime` values instead of asserting only that they are non-blank.

## Verification

### Backend focused test

Command (from `backend`):

```powershell
.\gradlew.bat --stop
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.store.StoreApiTest --no-daemon --rerun-tasks
```

Result: PASS. `BUILD SUCCESSFUL in 1m 3s`; all 5 Gradle tasks executed. The XML result reports `tests="13"`, `skipped="0"`, `failures="0"`, and `errors="0"`.

### Web production build

Command (from `web`):

```powershell
npm run build
```

Result: PASS. Both TypeScript projects completed without errors and Vite transformed 133 modules and produced the production bundle.

### Diff validation

Command (from the worktree root):

```powershell
git diff --check
git diff --cached --check
```

Result: PASS. No whitespace errors.

## Verification Limitation

The web package has no frontend test runner or `test` script, so the product ownership, purchasability, query-error, and accessibility branches could not receive automated component tests in this task. Verification is limited to the TypeScript/Vite production build and source-level review of those branches; no test framework or new product behavior was introduced.
