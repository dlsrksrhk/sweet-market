# Task 3 완료 보고서

## 구현 범위

- 구매자 쿠폰 발급 API를 추가했습니다.
  - `POST /api/coupon-campaigns/{campaignId}/claim`
  - 동일 회원·캠페인 중복 발급은 유니크 제약(`uq_member_coupons_campaign_member`) 충돌만 식별해 기존 발급 쿠폰을 재조회하여 반환합니다.
  - 중복 저장 시도는 별도 Spring 빈의 `REQUIRES_NEW` 트랜잭션에서 수행하므로, 충돌 롤백이 호출 서비스의 재조회 경로를 오염시키지 않습니다.
  - 미래, 일시중지, 종료 캠페인은 발급을 거부합니다.
- 구매자 쿠폰 탐색 및 지갑 API를 추가했습니다.
  - `GET /api/coupon-campaigns/available`
  - `GET /api/me/coupons`
  - 두 API는 `page`/`size` 페이지네이션을 지원합니다.
  - 탐색 쿼리는 현재 활성 발급 기간만 SQL에서 제한하고, 현재 회원의 `claimed` 상태를 단일 존재 조건으로 계산합니다.
  - 지갑 쿼리는 회원 조건을 SQL에서 적용하고 `issuedAt`, `id` 내림차순으로 정렬합니다.
  - 지갑 상태는 한 번 읽은 `Clock` 값으로 `ISSUED`, `USED`, `EXPIRED`, `UNAVAILABLE`를 계산하며, `UNAVAILABLE`일 때만 캠페인 유효 상태를 `unavailabilityReason`으로 반환합니다.
- 장바구니·주문·결제 동작은 수정하지 않았습니다.

## 테스트

새 통합 테스트를 먼저 추가한 뒤 구매자 엔드포인트 부재로 실패하는 것을 확인했습니다. 이후 구현 후 아래 명령을 다시 실행했습니다.

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponIssueApiTest' --tests 'com.sweet.market.coupon.CouponWalletApiTest' --rerun-tasks
```

결과: `BUILD SUCCESSFUL` (6개 테스트 통과)

검증 항목:

- 중복 발급의 동일 쿠폰 반환 및 단일 행 유지
- 공통 만료일/발급일 기준 유효기간 계산
- 미래/일시중지/종료 발급 거부
- 탐색 목록의 회원별 발급 상태와 지갑 격리
- `USED`, `EXPIRED`, `UNAVAILABLE` 상태 및 일시중지 사유

## 참고 사항

프로젝트 안내에 적힌 `C:\java\jdk-21` 경로는 현재 환경에 없어서, 설치된 JDK 21인 `C:\Users\kdh\.jdks\corretto-21.0.7`로 테스트를 실행했습니다.

## 리뷰 보완 (2026-07-14)

- `CouponIssueApiTest`에 두 발급 요청이 사전 중복 조회를 마친 뒤 동시에 별도 트랜잭션의 저장을 시도하도록 동기화한 통합 테스트를 추가했습니다. 실제 PostgreSQL 유니크 제약 충돌 뒤 두 요청이 같은 쿠폰을 받고 단일 행만 남는지 검증합니다.
- `CouponWalletApiTest`는 동일한 `issuedAt`을 가진 두 쿠폰의 `id` 내림차순 타이브레이커와 `size=2` 페이지 경계를 확인하도록 강화했습니다. 두 페이지에 걸쳐 `ISSUED`, `USED`, `EXPIRED`, `UNAVAILABLE` 및 `PAUSED` 사유를 모두 검증합니다.

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.coupon.CouponIssueApiTest' --tests 'com.sweet.market.coupon.CouponWalletApiTest' --rerun-tasks
```
