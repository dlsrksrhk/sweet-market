# Post Milestone 8 Roadmap Design

## Goal

Milestone 8 made Sweet Market usable as a small web-backed market demo. The next roadmap should keep the same principle: build practical, real-world-shaped features that deepen JPA learning without turning the project into an operations infrastructure project.

The project remains a JPA learning backend first. Frontend work should make backend behavior visible and demoable, not become a separate product-heavy track.

## Direction

The recommended next phase is a focused post-Milestone 8 roadmap:

```text
Milestone 9  -> automatic purchase confirmation and scheduler basics
Milestone 10 -> settlement operations refinement
Milestone 11 -> admin operations features
Milestone 12 -> seller reports and statistics
Optional     -> frontend usability polish across the above flows
```

This order keeps the learning curve natural:

1. Start with a small scheduled domain rule.
2. Use that rule to create more realistic settlement cases.
3. Add admin tools for reviewing and correcting operational states.
4. Finish with read-heavy reporting and aggregation queries.

## Non-Goals

These items are intentionally out of scope for the next roadmap:

- Real production deployment.
- Kubernetes, cloud infrastructure, or CI/CD pipeline depth.
- Real observability stack such as Prometheus, Grafana, Loki, or ELK.
- Distributed lock implementation beyond a simple learning-friendly guard.
- Real payment, courier, or file-storage provider integration.
- Multi-admin audit compliance.
- Full design system or frontend component library.
- Mobile app-level frontend polish.

The system may still use simple local logs, database state, and admin screens to make operational behavior understandable.

## Milestone 9: Automatic Purchase Confirmation And Scheduler

### Learning Goal

Learn how scheduled domain automation interacts with JPA transactions, dirty checking, idempotency, and time-based rules.

### Product Goal

Delivered orders can be automatically purchase-confirmed after a configured age, so the transaction can proceed toward settlement without a buyer manually pressing the confirm button.

### Scope

In scope:

- Add an automatic purchase confirmation use case for `DELIVERED` orders older than a configured threshold.
- Add a scheduler entry point for local/dev execution.
- Add a manual admin trigger endpoint or button if useful for demoing.
- Ensure repeated scheduler runs are idempotent.
- Keep manual purchase confirmation behavior intact.
- Add tests for threshold, idempotency, ownership/state rules, and transaction behavior.
- Surface the automatic confirmation result in the web order/admin views where useful.

Out of scope:

- Distributed scheduler coordination across multiple app nodes.
- Quartz or external scheduler infrastructure.
- Complex holiday/business-day calculations.
- Production-grade job monitoring.

### JPA Focus

- Transaction boundaries in scheduled services.
- Bulk update versus entity loading trade-offs.
- Dirty checking when transitioning order and product state.
- Time-based query predicates.
- Idempotent state transitions.

### Acceptance Criteria

- A delivered order older than the threshold becomes confirmed automatically.
- A recent delivered order remains delivered.
- Already confirmed or canceled orders are skipped safely.
- Re-running the scheduler does not duplicate side effects.
- Backend tests pass with JDK 21 and `JWT_SECRET`.
- The web demo can show at least one order affected by the flow.

## Milestone 10: Settlement Operations Refinement

### Learning Goal

Deepen settlement modeling with more realistic operational cases: pending states, retryable failures, duplicate prevention, and admin correction workflows.

### Product Goal

Admins and sellers can understand why a settlement was created, skipped, retried, or blocked.

### Scope

In scope:

- Review whether settlement status should remain simple or become more explicit, for example `PENDING`, `COMPLETED`, `FAILED`.
- Add settlement retry or re-run behavior for safe cases.
- Add admin settlement lookup filters by seller, status, order id, and date range.
- Add clear duplicate-settlement prevention behavior and tests.
- Add web admin screens for settlement search/detail if backend support is added.
- Keep seller settlement page simple but clearer.

Out of scope:

- Real bank transfer integration.
- Accounting ledger depth.
- External reconciliation file import.
- Production audit trail requirements.

### JPA Focus

- Unique constraints and race prevention.
- Query design for admin filters.
- DTO projections for settlement lists.
- Optimistic locking or database constraints for duplicate prevention.
- Transaction rollback behavior for partial failures.

### Acceptance Criteria

- Duplicate settlement creation remains impossible.
- Admin can inspect settlement records by practical filters.
- Retry behavior is explicit and idempotent.
- Seller settlement view still works.
- Tests cover success, duplicate, retry, and blocked cases.

## Milestone 11: Admin Operations Features

### Learning Goal

Practice admin-facing write operations that must respect domain invariants and security boundaries.

### Product Goal

An admin can inspect and manage core market state without directly changing the database.

### Scope

In scope:

- Admin product lookup and status inspection.
- Admin order lookup and lifecycle inspection.
- Admin member lookup by email or nickname.
- Admin-only operational actions that are safe for a learning project, such as hiding inappropriate products or viewing order detail.
- Clear authorization tests for admin/member/anonymous access.
- Web admin pages that use the APIs without deep operational infrastructure.

Out of scope:

- Admin role management UI.
- User suspension policy depth.
- Full moderation workflow.
- Audit compliance or legal logs.
- Customer support messaging.

### JPA Focus

- Read models for admin screens.
- Fetch join and projection choices across member/product/order relationships.
- Authorization boundaries around aggregate access.
- Avoiding N+1 in admin detail/list pages.
- Pagination and filtering trade-offs.

### Acceptance Criteria

- Admin can search products, orders, and members.
- Non-admin users cannot access admin APIs.
- Admin list endpoints avoid obvious N+1 behavior.
- Admin web screens can demonstrate the lookup flows.

## Milestone 12: Seller Reports And Statistics

### Learning Goal

Study read-heavy JPA queries, aggregation, DTO projection, pagination, and reporting trade-offs.

### Product Goal

Sellers can see simple business metrics about their sales and settlements.

### Scope

In scope:

- Seller dashboard summary:
  - active product count
  - sold-out product count
  - confirmed order count
  - settlement total
  - unsettled confirmed amount
- Time-windowed sales and settlement summaries.
- Product-level sales ranking or recent sales list.
- DTO projection-based query APIs.
- Web seller report page.

Out of scope:

- Real analytics warehouse.
- Event streaming.
- BI dashboards.
- Export to Excel or CSV unless added later as a small optional exercise.
- Caching layer unless a specific query motivates it.

### JPA Focus

- Aggregate queries.
- Group by and date range filters.
- DTO projection.
- Pagination limitations for grouped reports.
- Index-aware query design at a learning level.

### Acceptance Criteria

- Seller can view summary metrics from the web app.
- Backend report APIs are tested with multiple sellers and orders.
- Queries do not load large object graphs unnecessarily.
- Report values remain seller-scoped and cannot leak other sellers' data.

## Optional Frontend Track: Usability Polish

Frontend polish can happen alongside Milestones 9 to 12, but should not dominate them.

Useful improvements:

- Better loading and empty states.
- Pagination controls for product, order, settlement, and admin lists.
- Clearer status timelines for order lifecycle.
- Demo account quick-login helper for local profile only.
- Better responsive layouts for admin and seller screens.
- Route-level error boundaries.

Keep this track tied to backend learning. If a frontend improvement does not reveal or exercise backend behavior, defer it.

## Recommended Next Step

Start with Milestone 9.

It is the best next step because it is small, domain-centered, and connects naturally to the existing order, delivery, product status, settlement, and batch features. It also prepares better data and behavior for Milestone 10 settlement operations.

Milestone 9 should be planned as a separate implementation plan before coding. The plan should decide:

- automatic confirmation threshold
- scheduler profile/configuration
- whether to include a manual admin trigger
- query strategy for eligible delivered orders
- how the web demo should expose the result

## Verification Expectations For Future Milestones

Each future milestone should keep these checks:

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

Also keep the project rule that new JUnit `@Test` method names use Korean_with_underscores.

## Self-Review

- No production infrastructure work is included.
- Milestone ordering follows the current domain flow: delivery -> confirmation -> settlement -> admin operations -> reports.
- Each milestone has a clear JPA learning goal.
- Frontend work remains a supporting demo surface.
- Milestone 9 is small enough to become the next implementation plan.
