# M26 Task 5 실행 보고서

## 변경 사항

- `CouponQueryOptimizationTest`를 추가했습니다.
  - 선택상품 캠페인 25건을 시드해 사용가능 캠페인 첫 페이지가 대상 컬렉션을 초기화하지 않고 카드별 발급 조회 없이 20건을 반환하는지 검증했습니다.
  - 동일하게 쿠폰지갑 25건을 시드해 캠페인별 N+1 없이 첫 페이지 20건을 반환하는지 검증했습니다.
  - 쿠폰 발급 뒤 직접 주문과 장바구니 체크아웃이 M25 프로모션 가격 스냅샷만 저장하는지 검증했습니다.
- Hibernate 통계로 두 목록의 `collectionFetchCount == 0`, 준비 SQL 문 수 `<= 2`를 확인했습니다.
- 재사용 가능한 SQL assertion은 필요하지 않아 `QueryOptimizationTestSupport`는 변경하지 않았습니다.

## PostgreSQL 실행계획 증적

Docker Desktop에서 `postgres:17-alpine` 격리 컨테이너를 생성했습니다. 105,000개 캠페인, 105,000개 회원 쿠폰, 5,000개 대상 회원 쿠폰을 시드한 다음 `EXPLAIN (ANALYZE, BUFFERS)`를 수행했습니다.

| 경로 | 관측 결과 | 결론 |
| --- | --- | --- |
| 사용가능 캠페인 20건 | `coupon_campaigns_pkey` 역방향 스캔, 발급 `EXISTS`는 `idx_member_coupons_member_status_valid_until_id`를 한 번 Bitmap Scan하여 해시 집합화, 1.248 ms | 페이지별 쿼리이며 카드별 쿼리가 없습니다. |
| 지갑 ISSUED 20건 | `idx_member_coupons_member_status_valid_until_id`가 회원/상태/만료 조건에 Bitmap Index Scan으로 사용, 14.985 ms | 회원 지갑 범위가 인덱스로 축소됩니다. |
| 소유자·기간 관리 목록 20건 | `idx_coupon_campaigns_owner_lifecycle_issue_period` Index Scan, 0.665 ms | 소유자/기간 복합 인덱스가 사용됩니다. |
| 대상 | 세 목록 계획에 `coupon_campaign_targets`가 없음; 대상 인덱스 `idx_coupon_campaign_targets_product_campaign`은 상세/대상 경로를 위해 유지 | 목록은 대상 컬렉션을 fetch하지 않습니다. |

위 관측으로 추가 인덱스는 불필요했습니다.

## 검증

| 명령 | 결과 |
| --- | --- |
| `./gradlew.bat test --tests 'com.sweet.market.coupon.*' --tests 'com.sweet.market.promotion.*' --tests 'com.sweet.market.cart.*' --tests 'com.sweet.market.order.*' --tests 'com.sweet.market.payment.*' --rerun-tasks` | `BUILD SUCCESSFUL`, 169 tests / 22 classes, failure 0, error 0, skipped 0, 1m 06s |
| `./gradlew.bat test --rerun-tasks` | `BUILD SUCCESSFUL`, 597 tests / 86 classes, failure 0, error 0, skipped 0, 2m 57s |
| `npm run build` | 종료코드 0 |

`npm run build`는 518.00 kB 생산 JavaScript 청크 경고를 출력했습니다. 기존 번들 크기 경고이며 Task 5 변경으로 새로 생긴 실패는 아닙니다.

## 남은 범위

- M27에서 발급 용량/재고와 동시성 정책을 추가합니다.
- M28에서 쿠폰을 주문 가격에 적용하고 사용·취소·환불 수명주기를 처리합니다.
