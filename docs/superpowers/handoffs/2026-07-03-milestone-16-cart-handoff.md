# Milestone 16 Cart Handoff

## Completed

- Added buyer cart items with buyer/product uniqueness.
- Added idempotent cart add and remove APIs.
- Added `GET /api/me/cart` with available and unavailable cart rows.
- Added all-or-nothing cart checkout that creates one order per selected product.
- Added checkout response data with the generated order list.
- Added viewer-specific `carted` to product list and detail responses.
- Added web cart toggles on product cards and product detail.
- Added the authenticated `/me/cart` page with multi-select checkout.
- Navigated successful checkout to `/me/orders`.
- Fixed cart page selection state so checkout only submits currently selectable cart item IDs.

## Verification

- Backend full suite passed:
  - `cd backend`
  - `$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'`
  - `$env:PATH="$env:JAVA_HOME\bin;$env:PATH"`
  - `$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`
  - `.\gradlew.bat --no-daemon test`
- Web build passed:
  - `cd web`
  - `npm run build`
- Repo checks passed:
  - `git diff --check`

## Review Notes

- Each implementation task was reviewed for spec compliance and code quality.
- Task 7 code review found stale cart selection risks after cart query changes and item removal.
- The follow-up fix prunes selected IDs against current selectable IDs and removes selected state by the mutation target `cartItemId`.
- Task 7 re-review passed after the fix.

## Local Notes

- Cart checkout intentionally preserves the existing one-product `Order` model.
- A selected cart checkout is all-or-nothing.
- Unavailable cart rows remain visible so buyers can remove them manually.
- Cart counts are not public.
- The main checkout has a pre-existing local `backend/src/main/resources/application.yaml` change that was not touched.

## Follow-Up Candidates

- Reviews after confirmed purchases.
- Cancellation and refund flow.
- Product relisting and availability.
- Wishlist notifications.
