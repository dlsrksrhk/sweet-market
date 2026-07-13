# Task 7 Report: Responsive Buyer Catalog Discovery

## Delivered

- Added a reusable `CatalogPanel` for global discovery and fixed-store discovery.
- Added a buyer-facing `CatalogProductCard` that renders only thumbnail, title, price, category, availability, store identity/type, and wishlist/cart affordances.
- Replaced the home product list with the global catalog host.
- Retained the public store header and suspended-store empty state, while replacing active-store offset/page controls with the shared cursor catalog.
- Added normalized URL-backed keyword, category, price, availability, sales policy, store type, and sort controls. Every filter update clears `cursor`; only `더 보기` writes the next cursor.
- Added stale/error recovery through a first-page restart action.
- Added contained desktop filter rail/grid styling and a 760px native-button mobile filter drawer with `aria-expanded` and `aria-controls`.

## Personalization contract follow-up

The original catalog card contract did not expose a seller ID, which the existing wishlist/cart controls require to suppress controls for the viewer's own product. The approved Task 7 scope expansion adds the buyer-safe, non-rendered `sellerId` field end-to-end:

- Catalog SQL row projection selects `stores.owner_member_id`.
- Catalog row, API response, service mapping, and TypeScript contract carry `sellerId`.
- `CatalogProductCard` passes that value only to existing wishlist/cart controls.
- A focused API regression assertion verifies `sellerId` is returned.

## Verification

- `backend`: `gradlew.bat test --tests com.sweet.market.catalog.CatalogApiTest` with JDK 21 and `JWT_SECRET` — passed (6 tests).
- `web`: `npm run build` — passed.
- `web`: started Vite and confirmed `http://localhost:5173/` returned HTTP 200; process stopped after the check.
- `git diff --check` — passed.

No separate frontend test runner is configured in `web`; URL transitions, drawer accessibility wiring, and 390px overflow prevention were reviewed from the compiled component/CSS paths.
