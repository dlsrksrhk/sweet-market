# Milestone 15 Wishlist Design

## Goal

Milestone 15 adds a buyer-owned wishlist feature to Sweet Market.

Buyers should be able to save products they are interested in, remove saved products, and revisit their saved list later. The milestone should deepen JPA learning around buyer-owned relationships, uniqueness constraints, seller and buyer access rules, paged read models, and product summary projections.

## Context

Milestone 14 completed local product image uploads and representative thumbnails. Product cards and product detail pages now have realistic image data that wishlist screens can reuse.

The current product read model is centered on buyer-visible `ON_SALE` products:

```text
GET /api/products
GET /api/products/{productId}
```

Product statuses are:

```text
ON_SALE
RESERVED
SOLD_OUT
HIDDEN
```

The wishlist feature should keep saved relationships stable when a product later becomes reserved or sold out, while avoiding exposure of hidden products.

## Decisions

- New wishlist additions are allowed only for `ON_SALE` products.
- Existing wishlist entries remain visible when products later become `RESERVED` or `SOLD_OUT`.
- `HIDDEN` products are excluded from buyer wishlist responses without deleting the wishlist row.
- Buyers cannot wishlist their own products.
- Add and remove APIs are idempotent.
- Product list and detail responses include `wishlistCount`.
- Product list and detail responses include `wishlisted` for the current viewer, with `false` for anonymous users.
- The buyer wishlist page is sorted by newest wishlist entry first.
- The web UI uses a heart-style toggle on product cards and product detail.
- Anonymous users who press the wishlist action are sent to login.
- A seller viewing their own product sees a disabled "my product" state instead of a usable wishlist action.
- Wishlist sharing, alerts, recommendations, cart behavior, reviews, cancellation, and refund flows are out of scope.

## Non-Goals

- Wishlist sharing.
- Price drop alerts.
- Stock alerts.
- Recommendation logic.
- Cart or checkout behavior.
- Review behavior.
- Cancellation or refund behavior.
- Broad redesign of product cards, navigation, or the design system.
- Exposing hidden products to buyers through wishlist history.

## Backend Domain Design

Add a `WishlistItem` entity owned by a buyer and linked to one product.

Recommended fields:

```text
id
buyer
product
createdAt
```

Add a database uniqueness constraint for:

```text
buyer_id, product_id
```

The relationship should not cascade from wishlist item to product or member. Removing a wishlist item removes only the relationship row.

Domain rules:

- A buyer can have at most one wishlist item per product.
- A buyer cannot wishlist a product they own.
- A buyer can newly wishlist only an `ON_SALE` product.
- A wishlist item can continue pointing at a product that later becomes `RESERVED` or `SOLD_OUT`.
- Hidden products are filtered from buyer wishlist reads.

The application service should treat the unique constraint as a final guard, but should also check for existing rows before inserting so duplicate `POST` requests can return success without surfacing a conflict.

## Backend API Design

Add wishlist endpoints:

```text
POST /api/products/{productId}/wishlist
DELETE /api/products/{productId}/wishlist
GET /api/me/wishlist
```

`POST /api/products/{productId}/wishlist`:

- Requires authentication.
- Finds the product.
- Allows only `ON_SALE` products.
- Rejects the seller's own product.
- Creates a wishlist item if one does not already exist.
- Returns success if the wishlist item already exists.

`DELETE /api/products/{productId}/wishlist`:

- Requires authentication.
- Deletes the buyer's wishlist item when present.
- Returns success when the row is already absent.

`GET /api/me/wishlist`:

- Requires authentication.
- Returns a page sorted by `WishlistItem.createdAt desc`.
- Includes products with status `ON_SALE`, `RESERVED`, and `SOLD_OUT`.
- Excludes products with status `HIDDEN`.

Use this response for add and remove:

```text
productId
wishlisted
wishlistCount
```

This gives the frontend an authoritative state after each action.

## Read Model Design

Extend product summary and detail responses with wishlist fields:

```text
wishlistCount
wishlisted
```

For anonymous users, `wishlisted` should be `false`.

For authenticated users, `wishlisted` should indicate whether the current member has a wishlist row for the product. Product list queries should avoid per-row lazy count queries. Prefer DTO projections, batch lookup by page product ids, or repository methods that compute counts and viewer state without introducing N+1 behavior.

Add a wishlist-specific response for `GET /api/me/wishlist` instead of forcing the regular product summary response to carry wishlist metadata.

Use this response shape:

```text
wishlistItemId
productId
sellerId
sellerNickname
title
price
status
thumbnailUrl
wishlisted
wishlistCount
wishedAt
```

`wishlisted` is always `true` for rows returned from `/api/me/wishlist`. Keeping the field helps the web card logic stay consistent with product list and detail cards.

## Visibility And State Rules

Product discovery remains centered on `ON_SALE` products. A buyer can newly wishlist only products that are currently discoverable through the normal product experience.

Wishlist history is more stable than product discovery:

- If a wished product becomes `RESERVED`, it remains in `/me/wishlist`.
- If a wished product becomes `SOLD_OUT`, it remains in `/me/wishlist`.
- If a wished product becomes `HIDDEN`, it is omitted from `/me/wishlist`.

The hidden product row is not deleted automatically. If the seller later makes a product buyer-visible again in a future milestone, the old wishlist relationship can still be reused.

## Web Design

Add wishlist actions to existing product surfaces without a broad redesign.

Product cards:

- Show a wishlist toggle.
- Use the current `wishlisted` state to decide whether click calls `POST` or `DELETE`.
- Show `wishlistCount` near the action or product metadata.
- For anonymous users, clicking the action navigates to login.
- For the seller's own product, show a disabled "my product" state.

Product detail:

- Show the same wishlist toggle and count.
- Use the backend response after add or remove to refresh `wishlisted` and `wishlistCount`.
- Do not allow wishlist actions for the seller's own product.

Buyer wishlist page:

```text
/me/wishlist
```

- Requires login.
- Lists saved products newest first.
- Reuses product thumbnail behavior from Milestone 14.
- Shows status badges for `RESERVED` and `SOLD_OUT`.
- Does not show purchase or order actions for non-`ON_SALE` products.
- Lets the buyer remove items from the wishlist.
- Shows an empty state when the buyer has no visible wishlist items.

Shell navigation should include a modest link such as "Wishlist" or "Saved Products" for authenticated users.

## Frontend API Design

Extend:

```text
web/src/features/products/productApi.ts
```

Add these frontend API types and functions:

```text
ProductSummary.wishlistCount
ProductSummary.wishlisted
Product.wishlistCount
Product.wishlisted
WishlistItem
getMyWishlist()
addWishlist(productId)
removeWishlist(productId)
```

The UI can optimistically update the heart state, but it should use the API response as the final source of truth. While a wishlist request is pending, disable the action to avoid noisy repeated requests. Backend idempotency remains the authoritative protection for retries and double clicks.

## Validation And Errors

Add these error codes:

```text
WISHLIST_PRODUCT_NOT_ON_SALE
WISHLIST_OWN_PRODUCT_NOT_ALLOWED
```

`PRODUCT_NOT_FOUND` can be reused when the target product does not exist.

Duplicate add and missing delete should not produce errors because the API is idempotent. If two requests race and the database unique constraint rejects one insert, the service should catch the conflict, read the existing wishlist item, and return the same success response as a duplicate add.

## Testing Plan

Backend tests should cover:

- A buyer can add an `ON_SALE` product to the wishlist.
- Repeating add for the same product is idempotently successful.
- A buyer can remove a wished product.
- Removing an already absent wishlist item is idempotently successful.
- A buyer cannot wishlist their own product.
- A buyer cannot newly wishlist `RESERVED`, `SOLD_OUT`, or `HIDDEN` products.
- `/me/wishlist` returns newest wishlist entries first.
- `/me/wishlist` includes wished `RESERVED` and `SOLD_OUT` products.
- `/me/wishlist` excludes wished `HIDDEN` products.
- Product list responses include correct `wishlistCount` and viewer `wishlisted`.
- Product detail responses include correct `wishlistCount` and viewer `wishlisted`.
- Anonymous product list and detail responses set `wishlisted` to `false`.
- Duplicate wishlist rows are blocked by the database uniqueness constraint.

New JUnit `@Test` method names must use Korean_with_underscores.

Frontend verification should cover:

- `npm run build` passes.
- Product card and detail types include `wishlistCount` and `wishlisted`.
- Wishlist add and remove functions call the expected endpoints.
- `/me/wishlist` route is protected by login.
- Non-`ON_SALE` wishlist rows render with status badges and no purchase action.

Full verification commands:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

```powershell
cd web
npm run build
```

```powershell
git diff --check
git status --short --branch --untracked-files=all
```

## Acceptance Criteria

- Authenticated buyers can add an `ON_SALE` product to their wishlist.
- Authenticated buyers can remove a product from their wishlist.
- Add and remove operations are idempotent.
- Buyers cannot wishlist their own products.
- Buyers cannot newly wishlist non-`ON_SALE` products.
- Product list and detail pages show wishlist count.
- Logged-in buyers see whether each product is already wished.
- Anonymous users are sent to login when they try to wishlist.
- Sellers see a disabled own-product state instead of a wishlist action on their own products.
- `/me/wishlist` lists wished products newest first.
- `/me/wishlist` keeps `RESERVED` and `SOLD_OUT` products visible.
- `/me/wishlist` hides `HIDDEN` products.
- Backend tests pass with JDK 21 and `JWT_SECRET`.
- Web build passes.

## Future Roadmap

Wishlist preserves enough relationship history to support later interest-based features without implementing them in Milestone 15.

Future candidates:

- Product relisting or restock behavior for sold-out products.
- Notifications when a wished sold-out product becomes available again.
- Price change notifications for wished products.
- Stock alerts where the product model supports stock-like availability.

These features are intentionally out of scope for Milestone 15. The current design preserves wishlist relationships for `SOLD_OUT` products so a later milestone can build on that history.

## Self-Review

- The scope is one buyer-focused milestone.
- The design keeps product discovery simple by allowing new wishlist additions only for `ON_SALE` products.
- Wishlist history remains stable for `RESERVED` and `SOLD_OUT` products.
- Hidden products are not exposed to buyers.
- Idempotent add and remove behavior is explicit.
- Product summary projection requirements are explicit.
- Future restock, relisting, and notification ideas are captured without expanding the current implementation scope.
