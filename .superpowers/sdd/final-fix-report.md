# M27 Final Review Fix Report

## Scope

- Preserved the pre-existing dirty `.gitignore` change and did not modify any existing M26 task-report files.
- Added this uniquely named M27 final-fix report only.

## Fixes

1. Redis `SOLD_OUT` now reloads the campaign and validates its lifecycle/window before returning an issue-limit conflict. An inactive campaign returns `COUPON_LIFECYCLE_NOT_ALLOWED`.
2. `IN_PROGRESS` reservations now poll the durable member-coupon record and retry the Redis reservation every 50 ms for at most the 30-second reservation duration. This returns the already committed coupon, or retries after a release/expiry.
3. A Redis-unavailable failure from `complete` is isolated after the DB transaction commits. The durable coupon response is returned; subsequent claims find the coupon before Redis.
4. Unlimited responses now include explicit `issueLimit: null` and `remainingIssueCount: null`, and the web formatter treats both `null` and legacy omitted values as `발급 무제한`.
5. Regression coverage includes cache reinitialization from a nonzero durable issued count, lifecycle change during Redis sold-out handling, in-progress durability retry, complete failure, explicit null API fields, and V11 Flyway issue-limit schema constraints.

## Verification

Executed with JDK `C:\Users\kdh\.jdks\corretto-21.0.7` and Docker-backed Testcontainers:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.coupon.CouponIssueApiTest --tests com.sweet.market.coupon.RedisCouponIssuanceGateTest --tests com.sweet.market.coupon.CouponCampaignApiTest --tests com.sweet.market.coupon.CouponWalletApiTest --tests com.sweet.market.store.migration.CouponMigrationTest
```

Result: `BUILD SUCCESSFUL` (35 tests).

```powershell
cd web
npm run build
```

Result: TypeScript checks and Vite production build succeeded. Vite reported its existing >500 kB bundle-size warning.

`git diff --check` also completed without whitespace errors.
