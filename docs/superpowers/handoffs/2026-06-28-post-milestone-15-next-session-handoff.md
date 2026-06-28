# Post Milestone 15 Next Session Handoff

## Current State

- Current branch: `main`
- `main` is pushed to `origin/main`.
- Latest pushed commit:

```text
ecbfa3c docs: add milestone 15 handoff
```

- Milestone 15 is complete, merged into `main`, and pushed.
- The Milestone 15 worktree was removed:

```text
C:\dev\study\sweet-market\.worktrees\milestone-15-wishlist
```

- The local feature branch was deleted:

```text
codex/milestone-15-wishlist
```

- The local checkout still has a pre-existing local-only change:

```text
backend/src/main/resources/application.yaml
```

Do not stage, overwrite, reset, or discard that file unless the user explicitly asks. The local change sets development-friendly values such as `ddl-auto: update` and a local default `JWT_SECRET`.

- Another old worktree still exists and was not touched:

```text
C:\dev\study\sweet-market\.worktrees\milestone-14-product-images
```

## Completed In Milestone 15

Milestone 15 implemented Wishlist:

- Added the buyer wishlist relationship with uniqueness enforced by buyer/product.
- Added idempotent add and remove wishlist APIs:
  - `POST /api/products/{productId}/wishlist`
  - `DELETE /api/products/{productId}/wishlist`
- Added `GET /api/me/wishlist` as a protected newest-first page query.
- Kept wished `RESERVED` and `SOLD_OUT` products visible in wishlist results.
- Excluded `HIDDEN` products from wishlist results without deleting wishlist rows.
- Prevented buyers from wishlisting their own products.
- Restricted new wishlist additions to `ON_SALE` products.
- Added `wishlistCount` and viewer-specific `wishlisted` to product list/detail responses.
- Kept anonymous product reads public with real `wishlistCount` and `wishlisted=false`.
- Added web wishlist toggles on product cards and product detail.
- Added the authenticated `/me/wishlist` page.
- Routed anonymous wishlist clicks to login while preserving the current path/search/hash.
- Added a disabled own-product state for sellers viewing their own products.
- Added future roadmap notes for relisting, restock, and wishlist notifications.

Related documents:

```text
docs/superpowers/specs/2026-06-28-milestone-15-wishlist-design.md
docs/superpowers/plans/2026-06-28-milestone-15-wishlist.md
docs/superpowers/handoffs/2026-06-28-milestone-15-wishlist-handoff.md
```

## Verification Already Run

After merging Milestone 15 back to `main`, these commands passed from the main checkout:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```

```powershell
cd web
npm run build
```

```powershell
git diff --check
```

The final main push also succeeded:

```powershell
git push origin main
```

## Recommended Next Session Goal

Start Milestone 16:

```text
Milestone 16: Cart
```

Use a separate design document and implementation plan before coding.

Recommended documents:

```text
docs/superpowers/specs/2026-06-28-milestone-16-cart-design.md
docs/superpowers/plans/2026-06-28-milestone-16-cart.md
docs/superpowers/handoffs/2026-06-28-milestone-16-cart-handoff.md
```

Use an isolated worktree before implementation work.

Recommended worktree:

```text
C:\dev\study\sweet-market\.worktrees\milestone-16-cart
```

Recommended branch:

```text
codex/milestone-16-cart
```

## Likely Milestone 16 Direction

Based on the post-Milestone 13 roadmap, Milestone 16 should add a buyer cart feature after wishlist.

Recommended product goal:

```text
Buyers can collect products before ordering and place orders from selected cart items.
```

Recommended learning goal:

```text
Practice buyer cart aggregates, uniqueness constraints, checkout validation, product state re-checking, and transactional conversion from cart items to orders.
```

Likely in scope:

- Add product to cart.
- Remove product from cart.
- Buyer cart page.
- Prevent adding own products.
- Prevent duplicate cart items.
- Validate product availability at checkout time.
- Convert selected cart items into orders.
- Clear converted cart items after successful checkout.
- Keep unavailable cart rows visible enough for the buyer to remove them, while blocking checkout.
- Reuse product thumbnails and status badges from Milestones 14 and 15.

Likely out of scope:

- Quantity support for single-item used-market products.
- Coupons, promotions, or shipping fee calculation.
- Multi-seller checkout settlement complexity.
- Wishlist-to-cart automation.
- Restock/relisting/notification behavior.

## Follow-Up Roadmap Notes

The user explicitly wanted a future direction for:

- sold-out product restock
- product relisting or re-registration
- wishlist-based notifications

These are not part of Milestone 16 unless the user changes direction. Keep them as future roadmap candidates after the cart/review/cancellation track, or as a dedicated wishlist expansion milestone.

Recommended future milestone candidates:

```text
Milestone 19 -> Product Relisting And Availability
Milestone 20 -> Wishlist Notifications
```

Possible future behavior:

- A wished sold-out product can become buyer-visible again.
- Wishlist rows preserved during Milestone 15 can power notification eligibility.
- Buyers can receive notifications when a wished product is relisted or available again.

## Suggested First Commands Next Session

```powershell
cd C:\dev\study\sweet-market
git status --short --branch --untracked-files=all
git log --oneline --decorate -n 12
```

Then read:

```text
docs/superpowers/handoffs/2026-06-28-post-milestone-15-next-session-handoff.md
docs/superpowers/roadmaps/2026-06-27-post-milestone-13-roadmap.md
docs/superpowers/specs/2026-06-28-milestone-15-wishlist-design.md
docs/superpowers/plans/2026-06-28-milestone-15-wishlist.md
docs/superpowers/handoffs/2026-06-28-milestone-15-wishlist-handoff.md
```

## Notes For The Next Agent

- Follow `AGENTS.md`.
- New JUnit `@Test` method names must be Korean_with_underscores.
- Backend Gradle work should use JDK 21.
- Local backend tests should set `JWT_SECRET`.
- Prefer surgical changes and match existing code style.
- Keep roadmap, design spec, implementation plan, and handoff documents separate.
- Use superpowers brainstorming before designing Milestone 16.
- Use an isolated worktree before Milestone 16 implementation work.
- Do not touch the local-only `backend/src/main/resources/application.yaml` change unless explicitly asked.
