# Task 3 Report — Active Event Discovery Cache

## Delivered

- Added a Caffeine-backed `ActiveEventCache` for `List<ActiveEventResponse>` with one entry, a 30-second write TTL, and recorded statistics.
- Routed `DiscoveryQueryService.activeEvents()` through that cache without changing the public `ActiveEventResponse` DTO or controller response shape.
- Added `DiscoveryInvalidationEvent` and an `AFTER_COMMIT` transactional listener that invalidates the cache only after a successful transaction commit.
- Added committed invalidation publishing for promotion and coupon campaign content/lifecycle changes, product create/update/hide changes, direct inventory adjustment, purchase reservation, inventory reservation/release, and business-store status changes (including provisioning-time reactivation).
- Added Caffeine and Actuator dependencies; management exposure remains limited to `health,info` because this direct cache is not a Spring `CacheManager` cache.

## Tests

`ActiveEventCacheTest` verifies:

- A loader is called once for repeated cache reads.
- An event published in a committed transaction invalidates the entry.
- An event published in a rolled-back transaction leaves the entry intact.
- Test cleanup invalidates the shared cache to prevent cross-test state leakage.

## Verification

Executed from `backend` with JDK 21 and `JWT_SECRET` set:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.discovery.ActiveEventCacheTest' --tests 'com.sweet.market.promotion.*' --tests 'com.sweet.market.coupon.*' --rerun-tasks
```

Result: `BUILD SUCCESSFUL` (72.9 seconds).

## Review

Independent review identified a missed `StoreProvisioningService` reactivation path and insufficient commit/rollback listener coverage. Both were addressed before final verification. No unresolved critical or important findings remain.
