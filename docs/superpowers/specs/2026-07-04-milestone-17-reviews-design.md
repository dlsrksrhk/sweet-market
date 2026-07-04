# Milestone 17 Reviews Design

## Goal

Milestone 17 adds buyer reviews after confirmed purchases.

The product goal is that buyers can review completed purchases, and product detail pages can show trust signals through review lists and rating summaries.

The learning goal is to practice post-purchase write rules, one-review-per-order constraints, read models for product and seller reputation, and average rating aggregation.

## Context

Sweet Market already has the full order lifecycle:

```text
CREATED -> PAID -> SHIPPING -> DELIVERED -> CONFIRMED
```

`Order.confirm()` marks a product sold out and records `confirmedAt`. Seller reports and settlement flows already treat `CONFIRMED` as the business event that finalizes a purchase.

Milestone 16 added cart checkout while preserving the existing one-product `Order` model. Reviews should build on that model: one confirmed order can produce at most one review.

Confirmed orders make products `SOLD_OUT`, so review reads need product detail pages that can display completed products. Milestone 17 should broaden public product detail reads from only `ON_SALE` to buyer-visible statuses: `ON_SALE`, `RESERVED`, and `SOLD_OUT`. `HIDDEN` products should still not be publicly readable.

## Scope

In scope:

- Buyers can create a review for their own confirmed order.
- One review is allowed per order.
- Reviews include a 1 to 5 integer rating and short text content.
- My Orders shows a review entry point for confirmed orders.
- Product detail can show public detail for `ON_SALE`, `RESERVED`, and `SOLD_OUT` products.
- Product detail shows review summary and latest reviews.
- Product detail response includes product review count and average rating.
- Product detail response includes seller review count and average rating.
- Backend tests cover review write rules, duplicate prevention, product review reads, and rating summaries.

Out of scope:

- Review edit or delete.
- Review images.
- Review moderation, reporting, or admin workflows.
- Seller replies.
- Helpful votes.
- A separate My Reviews page.
- Showing ratings on product cards or seller profile redesign.

## Domain Model

Add a new `review` package with the established package structure:

- `review.api`
- `review.application`
- `review.domain`
- `review.repository`
- `review.query`

The core entity is `Review`.

`Review` fields:

- `id`
- `order`
- `buyer`
- `product`
- `seller`
- `rating`
- `content`
- `createdAt`

Persistence rules:

- Table name: `reviews`.
- Unique constraint: `order_id`.
- Index for product review list queries: `product_id, created_at, id`.
- Index for seller rating summary queries: `seller_id`.
- Rating is an integer from 1 to 5.
- Content is required and should be 10 to 500 characters.

`buyer`, `product`, and `seller` are derivable from `order`, but storing these relationships on `Review` keeps product and seller read models simple and avoids repeatedly joining through orders for common review queries. This milestone does not store product title, price, or seller nickname snapshots.

## Review Creation

Endpoint:

```text
POST /api/orders/{orderId}/review
```

Requires authentication.

Request body:

```json
{
  "rating": 5,
  "content": "거래가 빠르고 상품 설명도 정확했어요."
}
```

Behavior:

1. Validate the request body.
2. Load the order with buyer, product, and seller.
3. Reject missing orders.
4. Reject orders that do not belong to the authenticated buyer.
5. Reject orders that are not `CONFIRMED`.
6. Reject orders that already have a review.
7. Save and flush the new review.
8. Return the created review response.

The service should handle a unique constraint race as a duplicate review conflict. If two requests try to review the same order at once, only one review should be created.

Review creation does not change order, product, payment, delivery, or settlement state.

## Product Review Reads

Product review list endpoint:

```text
GET /api/products/{productId}/reviews
```

The endpoint is public.

Rules:

- Reviews are returned newest first.
- The first implementation should use standard pageable responses.
- Missing products return `PRODUCT_NOT_FOUND`.
- Review list visibility follows public product detail visibility.
- `ON_SALE`, `RESERVED`, and `SOLD_OUT` products can expose review lists.
- `HIDDEN` products return `PRODUCT_NOT_FOUND` for public review list reads.

Each review item should include:

- `reviewId`
- `orderId`
- `productId`
- `buyerId`
- `buyerNickname`
- `rating`
- `content`
- `createdAt`

## Rating Summary

Product detail responses should include:

- `reviewCount`
- `averageRating`
- `sellerReviewCount`
- `sellerAverageRating`

`averageRating` and `sellerAverageRating` should be nullable when there are no reviews. Counts should be `0` in that case.

Product list responses do not include rating summary in this milestone. This keeps list queries from accumulating more correlated subqueries after wishlist and cart enrichment.

Seller review summary is derived from all reviews for products owned by that seller. It is not limited to the currently viewed product.

Public product listing remains `ON_SALE` only. The broader `RESERVED` and `SOLD_OUT` visibility applies to product detail and review reads so completed products can show reviews without reintroducing unavailable products into the main marketplace list.

## My Orders UX

The existing `/me/orders` page is the review entry point.

For a `CONFIRMED` order:

- If the order has no review, show a `리뷰 작성` action.
- Clicking the action opens an inline form inside the order card.
- The form includes rating selection, content textarea, submit, and cancel.
- On success, invalidate my orders, product detail, product reviews, and product list queries.
- If the order already has a review, show `리뷰 작성 완료` instead of another write action.

To support this, `OrderSummaryResponse` should include:

- `reviewed`

The order detail response may include the same field for consistency if the implementation touches that DTO, but the required web flow only needs the list response.

## Product Detail UX

The existing product detail page should add a review section below the product description and purchase actions.

The section should show:

- Product average rating and review count.
- Seller average rating and seller review count.
- Latest product reviews with buyer nickname, rating, content, and created date.
- Empty state copy when there are no reviews yet.

The UI should stay compact and consistent with the current product detail design. This milestone should not redesign the full product page.

## Error Handling

Add review-specific error codes following the existing `BusinessException` and `ErrorCode` pattern:

- `REVIEW_ACCESS_DENIED` with `403` when the order does not belong to the authenticated buyer.
- `REVIEW_ORDER_NOT_CONFIRMED` with `409` when the order is not confirmed.
- `REVIEW_DUPLICATE` with `409` when the order already has a review.

No `REVIEW_NOT_FOUND` code is needed in this milestone because there is no review detail, edit, or delete API.

Validation errors should use the existing validation error response shape.

## API Summary

New endpoints:

- `POST /api/orders/{orderId}/review`
- `GET /api/products/{productId}/reviews`

Changed existing responses:

- Product detail response adds `reviewCount`, `averageRating`, `sellerReviewCount`, and `sellerAverageRating`.
- My order summary response adds `reviewed`.

No review update, delete, admin, or seller reply endpoints are added.

## Testing

Backend tests should cover:

- A buyer can review their own confirmed order.
- A buyer cannot review another buyer's order.
- A buyer cannot review an order that is not confirmed.
- A buyer cannot create a duplicate review for the same order.
- Duplicate review races result in a conflict and do not create two rows.
- Review rating must be between 1 and 5.
- Review content must be present and within length bounds.
- My order summaries include `reviewed=false` before a review and `reviewed=true` after a review.
- Product detail includes product review summary.
- Product detail includes seller review summary.
- Product detail can read `SOLD_OUT` products.
- Public product list still excludes `SOLD_OUT` products.
- Public product detail and review list reject `HIDDEN` products.
- Product review list returns latest reviews first.
- Product review list is pageable.

New JUnit `@Test` method names must be Korean with underscores.

Web verification should cover:

- Confirmed orders show the review creation action when not reviewed.
- Reviewed confirmed orders show the completed state.
- Product detail shows review summary and review list.
- Product detail shows an empty review state.
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

Good future milestones after the first review implementation:

- Review edit and delete.
- Review images.
- Seller replies.
- Review moderation or reporting.
- Product card rating snippets.
- Seller profile trust redesign.
- Cancellation and refund flow.

These follow-ups are outside Milestone 17.

## Self-Review

- Scope is one review-focused milestone.
- Reviews depend on confirmed orders and preserve the existing one-product order model.
- The design keeps product detail as the first read surface and my orders as the first write surface.
- Product lists are kept out of rating summary scope to avoid list query complexity.
- Edit, delete, moderation, and seller replies are intentionally deferred.
