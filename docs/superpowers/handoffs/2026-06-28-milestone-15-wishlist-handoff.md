# Milestone 15 Wishlist Handoff

## Completed

- Added the buyer wishlist relationship with uniqueness enforced by buyer/product.
- Added idempotent add and remove wishlist APIs.
- Added `GET /api/me/wishlist` as a newest-first page query.
- Kept reserved and sold-out products visible in wishlist results while hidden products are excluded.
- Added `wishlistCount` and `wishlisted` to product list and detail responses.
- Added web wishlist toggles on product cards and product detail.
- Added the `/me/wishlist` page for authenticated buyers.
- Routed anonymous users to login before wishlist actions.
- Added a disabled wishlist state for a member's own products.

## Verification

- Backend full suite passed:
  - `cd backend`
  - `$env:JAVA_HOME='C:\java\jdk-21'`
  - `$env:Path="$env:JAVA_HOME\bin;$env:Path"`
  - `$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`
  - `.\gradlew.bat --no-daemon test`
- Web build passed:
  - `cd web`
  - `npm run build`
- Repo checks passed:
  - `git diff --check`
  - `git status --short --branch --untracked-files=all`

## Local Notes

- Work was verified in `C:\dev\study\sweet-market\.worktrees\milestone-15-wishlist`.
- Branch: `codex/milestone-15-wishlist`.
- Wishlist availability intentionally follows product visibility rules: visible reserved and sold-out products remain visible; hidden products are excluded.
- Add/remove API behavior is intentionally idempotent so repeated user actions and retries do not fail on existing or missing wishlist rows.

## Follow-Up Candidates

- Restock flows remain out of scope.
- Relisting flows remain out of scope.
- Wishlist notifications remain out of scope.
