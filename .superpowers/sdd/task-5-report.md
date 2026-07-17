# Task 5 Report: Record and project coupon outcomes

## Status

Complete. Coupon claim and durable redemption outcomes now produce version-1 operational events and project idempotently into KST-hour campaign metrics without changing claim, purchase, payment, or compensation authorization.

## TDD evidence

### Initial RED

Command:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.operations.coupon.CouponOutcomeProjectionTest' --tests 'com.sweet.market.coupon.CouponIssueApiTest' --tests 'com.sweet.market.coupon.application.CouponRedemptionServiceTest'
```

Result: `BUILD FAILED` after 29 tests with 6 expected failures. Unlimited issuance still left `issued_count=0`, and the required claim/redemption metric counters remained `0`. The reservation-only characterization test already passed, proving the existing non-redemption boundary that had to remain unchanged.

Failing required tests:

- `같은_캠페인을_두번_발급해도_한장만_생성하고_같은_쿠폰을_반환한다`
- `새_쿠폰_발급_성공은_원본과_같은_트랜잭션에서_한번_집계한다`
- `이미_발급된_쿠폰_재요청은_ALREADY_CLAIMED로_집계한다`
- `발급한도_소진은_EXHAUSTED로_집계한다`
- `비활성_캠페인은_INACTIVE로_집계한다`
- `주문과_쿠폰사용이_커밋되면_사용_성공을_한번_집계한다`

### Initial GREEN

The same focused command completed with `BUILD SUCCESSFUL` in 57 seconds after adding the payload, event factory, recorder boundaries, and metric handler.

### Fallback RED/GREEN

Self-review identified that a successful pessimistic fallback could be emitted once as `SUCCESS` and then mislabeled by the outer synthetic `ALREADY_ISSUED` result.

Focused RED command:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponIssueApiTest.레디스_게이트를_사용할_수_없으면_데이터베이스_락으로_한도만_발급한다'
```

Result: `BUILD FAILED`; 20 failure outcomes were present where only the 15 genuine capacity losses were valid. After retaining whether the pessimistic fallback created or found the coupon, the same command completed with `BUILD SUCCESSFUL` in 38 seconds and asserted:

- claim successes: `5`
- `EXHAUSTED`: `15`
- `ALREADY_CLAIMED`: `0`

## Verification

Broader Task 5 coupon suite:

```powershell
.\gradlew.bat test --tests 'com.sweet.market.operations.coupon.CouponOutcomeProjectionTest' --tests 'com.sweet.market.coupon.*' --tests 'com.sweet.market.coupon.application.*'
```

Result: `BUILD SUCCESSFUL` in 1 minute 7 seconds.

Full backend regression:

```powershell
.\gradlew.bat test
```

Result: `BUILD SUCCESSFUL` in 4 minutes 25 seconds.

## Files

Created:

- `backend/src/main/java/com/sweet/market/operations/coupon/CouponOutcomeEventFactory.java`
- `backend/src/main/java/com/sweet/market/operations/coupon/CouponOutcomeReason.java`
- `backend/src/main/java/com/sweet/market/operations/coupon/CouponOutcomePayload.java`
- `backend/src/main/java/com/sweet/market/operations/coupon/CouponMetricEventHandler.java`
- `backend/src/test/java/com/sweet/market/operations/coupon/CouponOutcomeProjectionTest.java`
- `.superpowers/sdd/task-5-report.md`

Modified:

- `backend/src/main/java/com/sweet/market/coupon/application/CouponIssueService.java`
- `backend/src/main/java/com/sweet/market/coupon/application/CouponIssueTransactionService.java`
- `backend/src/main/java/com/sweet/market/coupon/application/CouponRedemptionService.java`
- `backend/src/test/java/com/sweet/market/coupon/CouponIssueApiTest.java`
- `backend/src/test/java/com/sweet/market/coupon/application/CouponRedemptionServiceTest.java`

## Self-review

- True issuance success records the member coupon, `issued_count`, and outbox event in one transaction for unlimited, conditional-limit, and pessimistic fallback paths.
- Existing claims and mapped claim failures retain their prior HTTP result/exception and use the separate best-effort failure recorder.
- Reservation and quote success do not count as redemption. Success is recorded only after reservation consumption and `MemberCoupon.markUsed()` in the payment transaction.
- Platform claims project with `commerce_store_id=0`; platform redemption retains owner scope `PLATFORM/0` while attributing the event to the ordered product's commerce store.
- Event time is truncated to an `Asia/Seoul` hour, and the PostgreSQL upsert increments exactly one typed counter.
- Duplicate delivery is deduplicated by the existing projection receipt coordinator; both claim and redemption success tests replay the same event and remain at `1`.
- No dashboard projection is read by claim, pricing, purchase, or payment authorization.
- M29 purchase idempotency and compensation code was not changed.
- Task 6 promotion/coupon applied, realized, canceled, and refunded amount projections were not preempted.
- `git diff --check` was clean before final staging. Existing unrelated Task 2–4 report modifications were preserved and excluded from the Task 5 commit.

## Concerns

No known functional concerns. The existing test runtime emits the repository's pre-existing Mockito dynamic-agent and class-data-sharing warnings; they did not affect test results.
