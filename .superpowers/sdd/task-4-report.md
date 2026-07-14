# Milestone 25 Task 4 Report: Buyer Price Read Models

## Scope

- Added `listPrice`, nullable `promotionId`/`promotionTitle`, `promotionDiscountAmount`, and `effectivePrice` to buyer product detail, legacy product summaries, cart rows, and storefront product cards.
- Product detail uses `PromotionPricingService.quote(product)` after the existing availability read.
- Product summaries, cart rows, and storefront pages collect current-page product IDs and make one bounded `quoteAll(productIds)` call before response mapping.
- Cart JPQL now returns `CartItemReadRow`; `CartQueryService` applies the price map while converting that row to the public response, preserving the existing availability, image, ordering, and checkout-state projection.

## TDD evidence

1. Added Korean-named API tests for active-promotion detail/legacy summary, cart refresh after pause/expiry, and storefront cards.
2. Ran the three requested suites before production implementation. The three new tests failed with `PathNotFoundException` for the absent price response fields, as expected.
3. Implemented the bounded mappings and reran the same command successfully.

## Verification

Executed with JDK 21 at `C:\Users\kdh\.jdks\corretto-21.0.7` and `JWT_SECRET=sweet-market-local-test-secret-key-32bytes-minimum`:

```powershell
cd backend
.\gradlew.bat test --tests 'com.sweet.market.product.ProductApiTest' --tests 'com.sweet.market.cart.CartApiTest' --tests 'com.sweet.market.store.StorefrontApiTest'
```

Result: `BUILD SUCCESSFUL` (71 tests, 0 failures).

## Review

- `price` remains the legacy list-price compatibility field; the new fields provide the current promotion quote explicitly.
- Missing price-map entries safely fall back to `PromotionPrice.withoutPromotion(price)`.
- No paged mapping calls `quote` per row.
