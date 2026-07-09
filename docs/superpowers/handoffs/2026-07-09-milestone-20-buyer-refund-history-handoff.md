# Milestone 20 Buyer Refund History Handoff

## Completed

- Added a buyer-scoped paginated refund request list API at `GET /api/refund-requests/me`.
- Returned only refund requests created by the authenticated buyer.
- Added optional status filtering for requested, approved, rejected, and all-status buyer views.
- Added `sellerId` and `sellerNickname` to refund request responses.
- Added buyer refund history page at `/me/refunds`.
- Moved seller refund operations from `/me/refunds` to `/me/sales/refunds`.
- Kept admin refund operations at `/admin/refunds`.
- Preserved My Orders refund request and refund status behavior.
- Added product and order navigation from buyer refund history rows.
- Added backend tests for buyer scoping, pagination, filtering, ordering, authentication, and response fields.

## Verification

- Backend full suite passed:
  - `cd backend`
  - `$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'`
  - `.\gradlew.bat --no-daemon test`
- Web build passed:
  - `cd web`
  - `npm run build`
- Repo hygiene passed:
  - `git diff --check`

## Local Notes

- Work was done in `C:\dev\jpa-study\.worktrees\milestone-20-buyer-refund-history`.
- Branch: `codex/milestone-20-buyer-refund-history`.
- The main checkout's local-only `backend/src/main/resources/application.yaml` change was not touched.

## Follow-Up Candidates

- Dedicated refund detail pages.
- Buyer refund cancellation.
- Buyer refund edit or reopen flow.
- Return shipping workflow.
- Refund evidence upload.
- Refund-related review rules.
