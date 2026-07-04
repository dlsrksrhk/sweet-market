# Milestone 17 Reviews Handoff

## Completed

- Added buyer reviews for confirmed orders.
- Enforced one review per order.
- Added product review list API.
- Added product and seller rating summaries to product detail.
- Added reviewed state to my order summaries.
- Broadened public product detail reads to buyer-visible `ON_SALE`, `RESERVED`, and `SOLD_OUT` products.
- Kept public product listing limited to `ON_SALE` products.
- Added inline review creation on `/me/orders`.
- Added review summary and latest reviews on product detail.

## Verification

- Backend full suite passed:
  - `cd backend`
  - `$env:JAVA_HOME='C:\java\jdk-21'`
  - `$env:PATH="$env:JAVA_HOME\bin;$env:PATH"`
  - `$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`
  - `.\gradlew.bat --no-daemon test`
- Web build passed:
  - `cd web`
  - `npm run build`

## Local Notes

- Review edit/delete, review images, moderation, seller replies, My Reviews, and product-card ratings remain out of scope.
- `backend/src/main/resources/application.yaml` has a pre-existing local-only development change in the main checkout and was not touched by this work.

## Follow-Up Candidates

- Cancellation and refund flow.
- Review edit and delete.
- Review images.
- Seller replies.
- Product card rating snippets.
