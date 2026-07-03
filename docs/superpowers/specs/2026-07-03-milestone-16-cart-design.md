# Milestone 16 Cart Design

## Goal

Milestone 16 adds a buyer cart feature after Wishlist.

The product goal is that buyers can collect products before ordering, review their cart later, select multiple cart items, and create orders from those selected items.

The learning goal is to practice buyer-owned cart relationships, uniqueness constraints, checkout validation, product state re-checking, and transactional conversion from cart items into existing one-product orders.

## Context

Sweet Market currently models an order as one buyer purchasing one product. `Order.create(buyer, product)` reserves the product and creates a single `Order`. Payment, delivery, confirmation, settlement, and reports already depend on this one-order-to-one-product model.

Milestone 15 added Wishlist as a buyer-owned product relationship with add/remove APIs, product read enrichment, and a buyer list page. Cart should follow that familiar shape where it fits, but it should remain a separate domain because cart items are order candidates rather than interest markers.

This milestone intentionally keeps the existing `Order -> Product` relationship. A cart checkout can create several `Order` rows, one per selected cart item, instead of introducing an order group or order item model.

## Scope

In scope:

- Add a cart item relationship owned by a buyer and linked to a product.
- Prevent duplicate cart items for the same buyer and product.
- Add and remove cart items from product cards and product detail.
- Show whether a product is already in the current viewer's cart.
- Add a protected buyer cart page.
- Let buyers select multiple cart items with checkboxes.
- Convert selected cart items into one `Order` per product.
- Delete successfully converted cart items after checkout.
- Keep unavailable cart items visible in the cart so buyers can remove them.
- Block checkout if any selected item is unavailable.
- Navigate to `/me/orders` after successful checkout.

Out of scope:

- Quantity support.
- Coupons, promotions, shipping fee calculation, or price negotiation.
- A parent order, checkout group, or order item model.
- Multi-seller bundled payment or bundled settlement.
- Wishlist-to-cart automation.
- Restock, relisting, or wishlist notification behavior.
- Real payment gateway changes.

## Domain Model

Add a new `cart` package with the established package structure:

- `cart.api`
- `cart.application`
- `cart.domain`
- `cart.repository`
- `cart.query`

The core entity is `CartItem`.

`CartItem` fields:

- `id`
- `buyer`
- `product`
- `createdAt`

Persistence rules:

- Table name: `cart_items`.
- Unique constraint: `buyer_id + product_id`.
- Index for product lookup during product-state queries: `product_id`.
- Index for list queries: `buyer_id, created_at, id`.

`CartItem.create(buyer, product)` sets the buyer, product, and current timestamp. Cart item domain logic should stay small; availability rules belong in application/query services because they depend on product state and buyer identity.

## Add And Remove Behavior

Add cart item:

- Endpoint: `POST /api/products/{productId}/cart`.
- Requires authentication.
- Loads the product with seller.
- Rejects missing products.
- Rejects the buyer's own product.
- Rejects products that are not `ON_SALE`.
- Returns success if the buyer already has the product in cart.
- Saves and flushes a new `CartItem` otherwise.
- Handles a unique constraint race as idempotent success when another request inserted the same buyer/product row first.

Remove cart item:

- Endpoint: `DELETE /api/products/{productId}/cart`.
- Requires authentication.
- Rejects missing products.
- Deletes by buyer id and product id.
- Returns success even if no row existed.

The response should include:

- `productId`
- `carted`

The response must not include public cart counts. Cart is a private buyer workflow, and this milestone only needs viewer-specific cart state.

## Product Read Enrichment

Product list and detail responses should include `carted`.

Rules:

- Anonymous product reads return `carted=false`.
- Authenticated product reads return true when the viewer has a cart item for that product.
- Existing wishlist enrichment remains unchanged.
- Product reads should not expose cart counts.

This mirrors the existing `wishlisted` viewer-specific read model while keeping cart as a private buyer workflow.

## Cart Query

Cart list endpoint:

- `GET /api/me/cart`
- Requires authentication.
- Returns a newest-first page.

Each item should include:

- `cartItemId`
- `productId`
- `sellerId`
- `sellerNickname`
- `title`
- `price`
- `status`
- `thumbnailUrl`
- `cartedAt`
- `checkoutAvailable`
- `unavailableReason`

Visibility rules:

- Cart rows remain visible for `ON_SALE`, `RESERVED`, `SOLD_OUT`, and `HIDDEN` products.
- `checkoutAvailable=true` only when the product is `ON_SALE` and not owned by the current buyer.
- `unavailableReason` should be null for available items.
- Unavailable reasons should be stable strings or enum-like values the web app can map to Korean copy.

The cart page should let buyers remove unavailable items manually rather than silently hiding or deleting them.

## Checkout

Checkout endpoint:

- `POST /api/me/cart/checkout`
- Requires authentication.
- Request body: `cartItemIds`.
- Response body: generated order list.

The response should use a checkout-specific wrapper named `CartCheckoutResponse` containing `orders`. Each generated order should be returned as an `OrderSummaryResponse`.

Checkout is all-or-nothing.

Transaction flow:

1. Validate that `cartItemIds` is not empty.
2. Validate that ids are unique.
3. Load all requested cart items with buyer, product, seller, and images needed for the response.
4. Validate that every requested cart item exists and belongs to the authenticated buyer.
5. Validate that every selected product is `ON_SALE`.
6. Validate that no selected product is owned by the buyer.
7. For each selected item, call the existing order creation path or equivalent domain logic so the product is reserved and an `Order` is saved.
8. Delete converted cart items.
9. Return generated orders.

If any selected item fails validation, no orders are created and no cart rows are deleted.

Concurrent checkout should rely on existing product versioning and `Product.reserve()` rules. If another transaction reserves a product first, this checkout should fail with a conflict and leave the cart unchanged.

## Error Handling

Add cart-specific error codes that match the existing `BusinessException` and `ErrorCode` pattern.

Recommended codes:

- `CART_ITEM_NOT_FOUND` with `404`.
- `CART_OWN_PRODUCT_NOT_ALLOWED` with `403`.
- `CART_PRODUCT_NOT_ON_SALE` with `409`.
- `CART_CHECKOUT_EMPTY` with `400`.
- `CART_CHECKOUT_INVALID_ITEMS` with `400` for duplicate ids or selected ids that cannot all be resolved as the authenticated buyer's cart items.
- `CART_CHECKOUT_NOT_ALLOWED` with `409` for unavailable selected products.

The first implementation can return the first failing reason. A detailed per-item failure response is useful later, but not required for this milestone.

## Web UX

Add a reusable cart toggle component similar to `WishlistToggle`.

Product card behavior:

- Show a cart button on buyer-visible product cards.
- Anonymous users are routed to login with the current path preserved.
- Sellers viewing their own products see a disabled own-product state.
- Logged-in buyers can add or remove the product from cart.
- The button reflects the latest mutation response and invalidates product/cart queries.

Product detail behavior:

- Keep the existing immediate `주문하기` action.
- Add cart toggle near the purchase action or product status area.
- The cart toggle should be available only for buyer interactions; own products stay disabled.

Cart page:

- Route: `/me/cart`.
- Navigation label: `장바구니`.
- Place the nav link between `찜한 상품` and `내 주문`.
- Show a list of cart items with thumbnail, title, seller, price, status, and remove action.
- Use checkboxes for selectable items.
- Disable selection for unavailable items.
- Provide a `선택 상품 주문하기` action.
- On successful checkout, invalidate products, cart, and orders, then navigate to `/me/orders`.
- Keep users on the cart page and show an error if checkout fails.

## API Summary

New protected endpoints:

- `POST /api/products/{productId}/cart`
- `DELETE /api/products/{productId}/cart`
- `GET /api/me/cart`
- `POST /api/me/cart/checkout`

Changed existing reads:

- Product list response adds `carted`.
- Product detail response adds `carted`.

No public cart endpoints are added.

## Testing

Backend tests should cover:

- A buyer can add an on-sale product to cart.
- Adding the same product repeatedly does not create duplicate rows.
- A buyer cannot add their own product to cart.
- A buyer cannot newly add a non-on-sale product to cart.
- Removing a product from cart is idempotent.
- Product list and detail include viewer-specific `carted`.
- Anonymous product reads return `carted=false`.
- The buyer cart list includes available and unavailable products.
- The buyer cart list marks only `ON_SALE` non-owned products as checkout available.
- Checkout creates one order per selected cart item.
- Checkout reserves each selected product.
- Checkout deletes converted cart items.
- Checkout returns generated orders.
- Checkout fails when the selected ids are empty.
- Checkout fails when selected ids contain duplicates.
- Checkout fails when a selected cart item belongs to another buyer.
- Checkout fails all-or-nothing when one selected product is unavailable.
- Failed checkout does not create any order.
- Failed checkout does not delete any cart item.

New JUnit `@Test` method names must be Korean with underscores.

Web verification should cover:

- Product cards show cart toggles.
- Product detail shows a cart toggle.
- `/me/cart` shows cart items and selection controls.
- Unavailable items are visible but not selectable for checkout.
- Successful checkout routes to `/me/orders`.
- `npm run build` passes.

## Verification

Use the established milestone checks:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

```powershell
cd web
npm run build
```

```powershell
git diff --check
```

Do not stage, overwrite, reset, or discard the existing local-only `backend/src/main/resources/application.yaml` change.

## Follow-Up Candidates

Good future milestones after Cart:

- Reviews after completed purchases.
- Cancellation and refund flow.
- Product relisting and availability.
- Wishlist notifications.
- Order grouping or order item remodeling if the project later needs bundled checkout, bundled payment, or multi-item order history.

These follow-ups are outside Milestone 16.

## Self-Review

- Scope is one buyer cart milestone.
- Existing one-product order behavior is preserved.
- Checkout is explicitly all-or-nothing.
- Unavailable cart rows are preserved for buyer visibility and manual removal.
- Product reads expose viewer-specific `carted`, not public cart counts.
- The design keeps roadmap, spec, plan, and handoff concerns separate.
