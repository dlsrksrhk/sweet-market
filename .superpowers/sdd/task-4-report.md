# Task 4 Report — Coupon Issuance Availability Read Contract

## Implemented

- Added `issueLimit` and `issuedCount` to the available-campaign JPQL projection.
- Derived buyer-facing `soldOut` from projected counts (`issueLimit != null && issuedCount >= issueLimit`) without loading coupon collections or issuing per-row count queries.
- Kept the active lifecycle/window predicate unchanged, so sold-out active campaigns remain in buyer discovery.
- Retained per-member `claimed` as the existing correlated `exists` projection.
- Confirmed owner summary/detail mappings already returned `issueLimit`, `issuedCount`, and nullable `remainingIssueCount` directly from the campaign/summary projection; added API coverage for limited and unlimited owner pages.

## Tests Added

- `소진된_캠페인은_목록에_남고_회원별_발급여부와_마감상태를_반환한다`
- `운영자_목록은_발급한도와_발급수와_잔여수를_반환한다`
- `운영자_목록의_무제한_캠페인은_발급한도와_잔여수를_반환하지_않는다`
- `사용가능_소진캠페인_한페이지도_카드별_발급조회없이_발급여부와_마감상태를_반환한다`

## Verification

- `./gradlew.bat testClasses` succeeded after the changes.
- The required targeted test command was attempted before implementation and cannot start in this environment because Testcontainers cannot find Docker (`com.docker.service` is stopped; `DockerClientProviderStrategy` throws `IllegalStateException`). Consequently, the API/query tests could not reach their assertions here. Re-run the targeted command with Docker running.

## Scope

- No issuance-flow or UI behavior was changed.
