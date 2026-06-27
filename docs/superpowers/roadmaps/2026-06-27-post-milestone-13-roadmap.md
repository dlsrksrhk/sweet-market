# Post Milestone 13 Roadmap

## Goal

Milestone 13 completed the seller reports expansion. The next phase should move Sweet Market from an operations-heavy learning track into buyer and product experience features that still deepen JPA practice.

The roadmap should keep roadmap management separate from individual milestone specs and plans. Each milestone still needs its own design document and implementation plan before coding.

## Direction

The recommended post-Milestone 13 roadmap is:

```text
Milestone 14 -> Product Images And Product UX
Milestone 15 -> Wishlist
Milestone 16 -> Cart
Milestone 17 -> Reviews
Milestone 18 -> Cancellation And Refund Flow
```

This order keeps the product experience practical:

1. Improve product presentation first, because later buyer features rely on product cards and detail pages feeling realistic.
2. Add wishlist as a small buyer-owned relationship before adding cart quantity and order conversion rules.
3. Add cart after wishlist so buyer collection behavior evolves from simple to transactional.
4. Add reviews after completed purchases exist as a natural business rule.
5. Add cancellation and refund flow last because it touches order, payment, delivery, settlement, and product state.

## Milestone 14: Product Images And Product UX

### Learning Goal

Practice aggregate child lifecycle, local file persistence, temporary upload cleanup, representative image constraints, ordering columns, and transaction boundaries between database state and filesystem writes.

### Product Goal

Sellers can register and edit products with real local image uploads, previews, representative image selection, and ordering. Buyers see realistic thumbnails and product detail galleries.

### Scope

In scope:

- Local product image upload.
- Temporary upload records and files.
- Scheduled cleanup for expired temporary uploads.
- Minimum one image for new products.
- Maximum 10 images per product.
- File validation for JPEG, PNG, and WebP up to 5MB each.
- Representative image selection.
- Image ordering with up/down controls.
- Product create/edit web UX for upload, preview, representative selection, ordering, and deletion.
- Product list/detail UX improvements around thumbnails and galleries.
- Read compatibility for existing URL-based image data.

Out of scope:

- S3 or external object storage.
- CDN integration.
- Image resizing, thumbnail generation, or transformation services.
- Drag and drop ordering.
- Broad design system rewrite.
- Wishlist, cart, review, cancellation, or refund logic.

## Milestone 15: Wishlist

### Learning Goal

Practice buyer-owned many-to-one relationships, uniqueness constraints, seller/buyer access rules, pagination, and product summary projections.

### Product Goal

Buyers can save products they are interested in and revisit them later.

### Scope

In scope:

- Add and remove wishlist items.
- Prevent duplicate wishlist rows per buyer and product.
- Hide or disable wishlist actions for the seller's own products.
- Show wishlist count or saved state where useful.
- Add a buyer wishlist page.
- Keep wishlist entries stable even if a product later becomes reserved, sold out, or hidden, while hiding products that should not be buyer-visible.

Out of scope:

- Wishlist sharing.
- Price drop alerts.
- Stock alerts.
- Recommendation logic.

## Milestone 16: Cart

### Learning Goal

Practice buyer cart aggregates, uniqueness constraints, checkout validation, product state re-checking, and transactional conversion from cart items to orders.

### Product Goal

Buyers can collect products before ordering and place orders from selected cart items.

### Scope

In scope:

- Add product to cart.
- Remove product from cart.
- Buyer cart page.
- Prevent adding own products.
- Prevent duplicate cart items.
- Validate product availability at checkout time.
- Convert selected cart items into orders.

Out of scope:

- Quantity support for single-item used-market products.
- Coupons, shipping fee calculation, or promotions.
- Multi-seller checkout settlement complexity.

## Milestone 17: Reviews

### Learning Goal

Practice post-purchase write rules, one-review-per-order constraints, seller/product read models, and average rating aggregation.

### Product Goal

Buyers can review completed purchases, and product/seller pages can show trust signals.

### Scope

In scope:

- Review creation only for confirmed purchases.
- One review per order.
- Rating and short content.
- Product detail review list.
- Seller/product rating summary.
- Review edit or delete if scope remains manageable.

Out of scope:

- Review images.
- Review moderation workflow.
- Helpful votes.
- Full seller profile redesign.

## Milestone 18: Cancellation And Refund Flow

### Learning Goal

Practice complex state transitions across order, payment, delivery, settlement, and product aggregates with rollback-safe rules.

### Product Goal

Buyers and sellers can handle realistic cancellation and refund cases without direct database changes.

### Scope

In scope:

- Cancel before payment or before delivery starts.
- Refund request for paid orders within allowed states.
- Product reservation restoration when cancellation is allowed.
- Settlement blocking for refunded orders.
- Admin or seller visibility into cancellation/refund state.
- Tests for invalid state transitions and idempotency.

Out of scope:

- Real payment gateway refund API.
- Dispute mediation.
- Partial refund.
- Return shipping logistics.
- Legal audit depth.

## Verification Expectations

Each milestone should keep the established checks:

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
```

New JUnit `@Test` method names must use Korean_with_underscores.

Do not stage or overwrite `backend/src/main/resources/application.yaml`; it has an existing local-only development change in the main checkout.

## Recommended Next Step

Start Milestone 14 with a separate design document and implementation plan:

```text
docs/superpowers/specs/2026-06-27-milestone-14-product-images-and-product-ux-design.md
docs/superpowers/plans/2026-06-27-milestone-14-product-images-and-product-ux.md
```

Use an isolated worktree before Milestone 14 implementation work.

## Self-Review

- The roadmap is separate from individual milestone specs and plans.
- The ordering moves from product presentation to buyer collection, checkout, trust, and reversal flows.
- Each milestone has a practical commerce feature and a JPA learning focus.
- Product image upload is kept in Milestone 14 instead of leaking into later buyer features.
- Infrastructure-heavy work remains out of scope.
