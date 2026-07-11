# M22 Final Review Fixes Report

## Scope

- Base commit: `25636c6`
- Web-only review fixes; no backend, docs, dependency, or test-framework changes.

## Files

- `web/src/features/stores/storeApi.ts`
  - Adds `StorefrontViewerKey` and places it in every public storefront product-list query key beneath the existing `publicProducts(storeId)` prefix.
- `web/src/pages/StoreProfilePage.tsx`
  - Uses the resolved member ID or the stable `'anonymous'` marker for product-list queries.
  - Pauses product-list fetching while authentication identity is unresolved.
- `web/src/features/auth/AuthProvider.tsx`
  - Removes the `['stores', 'public']` subtree at every existing authenticated-query cleanup transition.
  - Marks explicit login/member refresh work as loading so a token-bearing request cannot populate the anonymous product cache while member identity is unresolved.
- `web/src/features/stores/StoreCatalogPanel.tsx`
  - Extracts shared catalog reconciliation.
  - On command failure, preserves the API error, refreshes summary/operator/public/global product state, refreshes the operable-store list, then clears selection.

## Exact Behavior

### Viewer-private public storefront cache

Public catalog list keys now have this shape:

`['stores', 'public', storeId, 'products', viewerId | 'anonymous', status, sort, page, size]`

The viewer segment is below `storeQueryKeys.publicProducts(storeId)`. Existing operator/product mutation invalidations continue to match every viewer variant without call-site changes. The header retains its viewer-independent key `['stores', 'public', storeId]`; auth cleanup intentionally removes it with the broader subtree, after which an active header query fetches the same public representation again.

All existing calls to `clearAuthenticatedPrivateQueries` now also remove `['stores', 'public']` with `exact: false`, covering login replacement, logout, failed explicit refresh, failed startup refresh, and login failure. Catalog requests are disabled while auth identity is loading.

### Failed hide/show reconciliation

Successful hide/show keeps its prior invalidation behavior through `reconcileCatalogQueries`:

- selected-store summary;
- selected-store operator catalog subtree;
- selected-store public catalog subtree;
- general product subtree.

Failure runs the same reconciliation and additionally invalidates the operable-store list so suspension or permission changes update the selected store's status/availability. `Promise.allSettled` ensures every invalidation is attempted and selection is cleared after reconciliation. The mutation error is set before reconciliation and is not cleared by it.

## Verification Evidence

- Pre-fix source transition check: failed with all five expected gaps (no viewer key, no anonymous marker, no public-subtree cleanup, no failure invalidation, no failure selection clear).
- Post-fix source transition check: `PASS: viewer/auth and failed-command source transitions are present`; `rg` reports one `publicProductList` call site, updated to pass `viewerKey`.
- `npm run build` from `web`: exit `0`; both TypeScript checks passed and Vite built 138 modules.
- `git diff --check`: exit `0`.

## Self-review

### Anonymous -> account A -> logout -> account B

1. Anonymous uses the `'anonymous'` list-key segment.
2. Login sets auth loading before token/member replacement, disables catalog fetching, and clears the entire public subtree before installing the new token.
3. Once account A resolves, its catalog fetch uses member ID A; anonymous data cannot satisfy that key.
4. Logout clears the public subtree and switches to a fresh `'anonymous'` key with no token.
5. Account B login repeats subtree removal and identity gating; the eventual list key uses member ID B, so neither anonymous nor account A `wishlisted`/`carted` flags can satisfy it.
6. The public header is viewer-independent and is safely refetched after each subtree removal.

### 403/409 stale recovery

1. The API error is converted to the existing visible Korean error message immediately.
2. Summary and operator catalog active queries refetch, reconciling `catalogWritable`, row status, and row membership with server truth.
3. The operable-store list refetches on failure, reconciling suspension or lost access; the public and general product prefixes are also invalidated.
4. All invalidations settle before selection is cleared, preventing stale selected IDs from driving another command.
5. The error state is not reset during reconciliation; it remains visible while the panel remains mounted.

## Concerns

- No automated browser/component test framework exists and adding one was explicitly out of scope. Verification therefore uses the approved source-level transition review plus the production TypeScript/Vite build.
