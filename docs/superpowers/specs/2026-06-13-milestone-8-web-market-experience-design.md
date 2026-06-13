# Milestone 8 Web Market Experience Design

## Goal

Milestone 8 adds the first real web experience for Sweet Market.

The product goal is that a user can use the project like a small secondhand market service: browse products, sign up or log in, create orders, move a transaction through payment and delivery, confirm purchase, and inspect settlements.

The learning goal is to connect the JPA-backed backend flows to a realistic frontend while keeping the project focused on backend and JPA learning. The web app should reveal API boundaries, state transitions, relationship loading, and query behavior through normal user workflows. It should not turn this project into an operations infrastructure project.

## Design Direction

Add a `web` app at the repository root.

```text
sweet-market
  backend
  web
  docs
```

The backend remains the source of truth and the web app consumes the existing REST APIs. Backend changes in this milestone should be limited to demo support and the smallest admin API gap needed by the web app.

Recommended flow:

```text
local seed data
-> user/admin login
-> market browsing and transaction screens
-> admin settlement batch execution
-> admin settlement batch execution history
```

This milestone should make the existing backend feel like a complete market application without introducing production deployment, OAuth, refresh tokens, file storage, or deep operational tooling.

## Scope

In scope:

- Add a React, Vite, TypeScript web app under `web`.
- Add local or dev profile demo seed data.
- Seed admin, seller, buyer, product, order, payment, delivery, and settlement examples.
- Add admin settlement batch execution history read APIs.
- Add local web CORS support for the Vite dev server.
- Add user login and signup screens.
- Add role-aware routing for member and admin users.
- Add product list, detail, create, edit, hide, and image management flows where supported by the backend.
- Add order creation and cancellation flows.
- Add payment approval and cancellation flows.
- Add delivery start and complete flows.
- Add purchase confirmation flow.
- Add my orders, my sales, and my settlements screens.
- Add admin settlement batch launch and recent execution history screens.
- Add frontend build and typecheck verification.
- Keep backend tests passing with JDK 21.

Out of scope:

- Real production deployment.
- OAuth or social login.
- Refresh token flow.
- Real file upload or object storage.
- Real payment or delivery provider integration.
- Public admin account creation.
- Admin role management UI.
- Scheduler.
- Distributed lock.
- Batch stop or restart APIs.
- Batch failure detail persistence table.
- Full mobile app polish.
- Large design system or component library.

## Backend Additions

### Demo Seed Data

Add a local or dev profile seed component, for example:

```text
DemoDataInitializer
```

It should create deterministic demo data only in a local development profile. The seed must be idempotent so repeated application startup does not duplicate demo rows.

Seed accounts:

```text
admin@example.com / password123
seller1@example.com / password123
seller2@example.com / password123
buyer1@example.com / password123
buyer2@example.com / password123
```

Seed data should include:

- ADMIN member.
- Multiple seller and buyer members.
- Products in visible market states.
- Orders across the main lifecycle states.
- Payment rows for paid examples.
- Delivery rows for shipping and delivered examples.
- Settlement rows for completed settlement examples.
- At least one confirmed but unsettled order so the admin batch screen can visibly create a settlement.

The initializer should use domain methods where possible instead of directly mutating fields. This keeps the demo data aligned with the business rules the project is meant to teach.

### Admin Batch History API

The current admin batch API launches the settlement job:

```text
POST /api/admin/batches/settlements
```

Add read APIs for recent executions:

```text
GET /api/admin/batches/settlements/executions?size=20
GET /api/admin/batches/settlements/executions/{executionId}
```

Responses should expose enough metadata for a useful admin console:

```text
executionId
jobName
status
exitCode
createTime
startTime
endTime
confirmedBefore
limit
chunkSize
readCount
writeCount
skipCount
rollbackCount
failureMessages
```

Use `JdbcTemplate` for Spring Batch metadata reads instead of mapping Batch metadata tables as JPA entities. The metadata tables are infrastructure-owned, not Sweet Market domain aggregates.

### CORS

Allow the Vite dev server in local development:

```text
http://localhost:5173
```

Keep this scoped to local or dev configuration. Do not broaden production-style security policy in this milestone.

## Web Architecture

Use:

```text
React
Vite
TypeScript
React Router
TanStack Query
React Hook Form
```

Avoid a heavy UI component library in the first version. Simple local components and CSS are enough. This keeps attention on API flow, state transitions, and the backend model.

Recommended structure:

```text
web
  src
    app
      App.tsx
      router.tsx
      providers.tsx
    pages
      HomePage.tsx
      LoginPage.tsx
      SignupPage.tsx
      ProductListPage.tsx
      ProductDetailPage.tsx
      ProductFormPage.tsx
      MyOrdersPage.tsx
      MySalesPage.tsx
      MySettlementsPage.tsx
      AdminBatchPage.tsx
    features
      auth
      products
      orders
      payments
      deliveries
      settlements
      admin
    shared
      api
      components
      layout
      utils
```

## Routing

Public routes:

```text
/                  product list
/login             login
/signup            signup
/products/:id      product detail
```

Authenticated member routes:

```text
/products/new
/products/:id/edit
/me/orders
/me/sales
/me/settlements
```

Admin routes:

```text
/admin/batches/settlements
```

The web app should route by role:

- Unauthenticated users can browse product list and detail.
- Authenticated members can create products and progress their own transaction flows.
- ADMIN users can access the admin batch screen.
- MEMBER users who open admin routes see an access denied state.

## API Client

Create a small fetch-based API client:

```text
shared/api/http.ts
```

Responsibilities:

- Store base API URL.
- Attach `Authorization: Bearer <token>` when available.
- Send and receive JSON.
- Unwrap backend `ApiResponse<T>` responses.
- Normalize backend error responses for screens and forms.

Frontend error shape:

```text
code
message
fieldErrors
```

Do not hide all errors behind generic messages. A learning project benefits from visible error codes because they show which backend rule rejected the action.

## Auth State

Store the access token in `localStorage` for the first version.

This is acceptable for this local learning project. Do not add refresh tokens or cookie-based auth in this milestone.

Auth flow:

```text
login/signup
-> store access token
-> GET /api/members/me
-> keep current member state
```

Add:

```text
useAuth
RequireAuth
RequireAdmin
```

The current member state should include:

```text
id
email
nickname
role
```

## Server State

Use TanStack Query for server state:

- Products.
- Product detail.
- My orders.
- My sales.
- My settlements.
- Admin batch executions.

Mutation invalidation examples:

```text
product create -> invalidate product list and my sales
order create -> invalidate product detail and my orders
payment approve -> invalidate my orders and order detail
delivery complete -> invalidate my orders and order detail
purchase confirm -> invalidate my orders, settlements, and product detail
admin batch launch -> invalidate admin batch executions and settlements
```

## User Market Screens

### Product List

Primary landing page.

Show:

- Product title.
- Price.
- Status.
- Seller nickname when available.
- Thumbnail or fallback image.

The page should feel like a small market storefront, not an admin dashboard.

### Product Detail

Show product information and allowed actions:

- Order product when available.
- Edit or hide when current user is the seller.
- Show status if reserved or sold out.

### Transaction Screens

The first version can make state transitions explicit with buttons:

```text
create order
approve payment
start delivery
complete delivery
confirm purchase
```

This is intentionally a little demo-like. It makes the backend lifecycle visible and keeps the project focused on learning.

### My Pages

Add:

```text
My Orders
My Sales
My Settlements
```

These screens should expose the domain state clearly:

- Order status.
- Product status.
- Payment status.
- Delivery status.
- Settlement status.

## Admin Console

The admin console is intentionally small.

Screen:

```text
/admin/batches/settlements
```

Sections:

- Batch launch form.
- Recent executions table.
- Execution detail panel.

Launch form:

```text
confirmedBefore
limit
chunkSize
```

Execution table:

```text
executionId
status
startTime
endTime
readCount
writeCount
skipCount
```

This gives Milestone 7's Spring Batch work a visible UI without expanding into a full operations suite.

## Testing Strategy

Backend:

- Demo seed data runs only for the intended local or dev profile.
- Seed data creation is idempotent.
- Admin batch history list API returns recent settlement job executions.
- Admin batch history detail API returns step counts and parameters.
- MEMBER cannot access admin batch history APIs.
- Full backend test suite passes with JDK 21.
- New JUnit `@Test` method names use Korean_with_underscores.

Frontend:

- Build passes.
- Typecheck passes.
- API client unwraps success responses and normalizes error responses.
- Auth state stores token and loads current member.
- Route guards redirect or block unauthenticated and non-admin users.
- Key pages render loading, success, and error states.

End-to-end manual verification:

```text
seed data startup
-> login as buyer
-> browse product
-> create order
-> approve payment
-> complete delivery
-> confirm purchase
-> login as admin
-> run settlement batch
-> verify execution history
```

## Acceptance Criteria

- `web` app exists and starts on Vite's local dev server.
- Demo credentials are documented and usable.
- User can log in, browse products, create an order, approve payment, complete delivery, and confirm purchase from the web app.
- Seller can log in, create a product, inspect sales, and inspect settlements from the web app.
- Admin can log in, launch settlement batch, and inspect recent settlement batch executions from the web app.
- Backend tests pass with JDK 21.
- Web typecheck and build pass.
- No production deployment or deep operations infrastructure is required.

## Future Milestones

Good follow-up milestones after this one:

- Automatic purchase confirmation after delivery age.
- Scheduler and duplicate execution prevention.
- Seller report and statistics screens.
- Batch execution monitoring improvements.
- Frontend usability polish and responsive refinement.
