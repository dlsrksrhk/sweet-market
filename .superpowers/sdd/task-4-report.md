# M24 Task 4 Report: Buyer Catalog APIs

## Delivered

- Added public `GET /api/catalog/products` and `GET /api/stores/{storeId}/catalog/products` endpoints returning `CatalogSearchResponse`.
- Added `CatalogSearchQueryService` to share request-to-criteria conversion, full cursor fingerprint validation, `size + 1` page trimming, next-cursor construction, and bounded wishlist/cart ID-set personalization.
- Store-scoped searches reject query-string `storeId` and require the route store to be `ACTIVE`; unknown, pending, rejected, and suspended stores return `STORE_NOT_FOUND`.
- Kept the existing offset storefront endpoint unchanged. The new card response only exposes buyer availability and does not expose total/reserved inventory or audit data.
- Added public security matchers for the two new optional-viewer GET endpoints.

## API Test Coverage

- Combined keyword/category/price/availability/sales-policy/store-type filtering and buyer-safe response shape.
- Anonymous and authenticated wishlist/cart flags.
- Keyset cursor page continuation plus expired, tampered, and fingerprint-mismatched cursors.
- Invalid ranges, enum values, and sizes.
- Store-route query `storeId` rejection and inactive/unknown route-store policy.

## TDD Evidence

- `CatalogApiTest` was added before endpoint implementation. The focused test run failed with 404 responses for the missing endpoints.
- After endpoint/service implementation, anonymous requests initially returned 401 because the public routes were not yet authorized. Adding the two minimal GET security matchers resolved that integration failure.
- Store-route tests then exposed path-variable binding into the `@ModelAttribute` `storeId`; the controller removes that path-derived value only when no query `storeId` is supplied, leaving the service to reject actual query values.

## Verification

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.catalog.CatalogApiTest' --tests 'com.sweet.market.product.ProductApiTest' --tests 'com.sweet.market.store.StorefrontApiTest' --rerun-tasks
```

Result: `BUILD SUCCESSFUL`.

## P1 Follow-up: Blank Fixed-Route Store Filter Rejection

- Added MockMvc assertions that both `?storeId=` and `?storeId=   ` on `GET /api/stores/{storeId}/catalog/products` return `VALIDATION_ERROR`.
- RED: the focused `CatalogApiTest` run failed because an empty query value bound to a null `CatalogSearchRequest.storeId` and the endpoint returned 200.
- GREEN: the storefront controller now rejects when the raw request parameter map contains `storeId`, before stripping the path-variable value from the model-bound request. A route with no query `storeId` still passes the fixed route-store context to the shared service.

Verification:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.catalog.CatalogApiTest' --tests 'com.sweet.market.store.StorefrontApiTest' --rerun-tasks
```

Result: `BUILD SUCCESSFUL`.
