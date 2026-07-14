# M26 쿠폰 캠페인·표준 쿠폰 발급 핸드오프

## 제공 범위

- 상점 소유자 API: `/api/stores/{storeId}/coupon-campaigns`에서 생성·목록·상세·수정과 `schedule`, `pause`, `resume`, `end` 전환을 제공합니다.
- 플랫폼 관리자 API: `/api/admin/coupon-campaigns`에서 동일한 캠페인 관리 기능을 제공합니다.
- 구매자 API: `GET /api/coupon-campaigns/available`, `POST /api/coupon-campaigns/{campaignId}/claim`, `GET /api/me/coupons`을 제공합니다.

상점 캠페인은 해당 상점의 OWNER만 관리할 수 있고, 플랫폼 캠페인은 관리자 영역으로 분리됩니다. `STORE` 소유자는 자신의 상점 상품만 선택 대상으로 지정할 수 있으며, `PLATFORM` 소유자는 상점을 갖지 않습니다.

## 유효성·발급 규칙

- 캠페인은 `DRAFT → SCHEDULED → PAUSED/SCHEDULED → ENDED` 수명주기를 사용합니다. 실제 발급 가능 여부는 예약 상태와 발급 시작·종료 시각을 함께 검사합니다.
- 유효기간은 `COMMON_EXPIRY`(공통 만료시각) 또는 `DAYS_FROM_ISSUANCE`(발급시각 기준 일수) 중 하나입니다. 발급된 `MemberCoupon`에는 할인·범위·스택 가능 여부·만료시각 스냅샷이 저장됩니다.
- 동일 회원과 캠페인 조합은 유니크 제약조건으로 한 장만 생성됩니다. 선행 조회와 유니크 충돌 재조회로 중복 요청 및 동시 요청에도 같은 쿠폰을 반환합니다.
- 지갑 상태는 사용됨, 만료됨, 캠페인 비가용, 발급 가능을 분리하며 `issuedAt DESC, id DESC` 순서로 페이지를 반환합니다.

## M25 체크아웃 경계

M26은 쿠폰을 발급·조회만 하며 주문/장바구니 체크아웃에 쿠폰 가격 계산이나 쿠폰 스냅샷을 추가하지 않습니다. 직접 주문과 장바구니 체크아웃은 기존 M25 `promotion_campaign_id`, `promotion_discount_amount`, `final_price` 스냅샷만 계속 기록합니다.

## 쿼리 예산 및 PostgreSQL 실행계획 증적

`postgres:17-alpine` 독립 컨테이너에서 105,000개 캠페인과 105,000개 회원 쿠폰(대상 회원 5,000개)을 시드하고 `EXPLAIN (ANALYZE, BUFFERS)`를 실행했습니다.

- 사용가능 캠페인 20건: `coupon_campaigns_pkey` 역방향 인덱스 스캔으로 페이지를 만들고, 발급 여부 `EXISTS`는 회원 쿠폰을 한 번만 Bitmap Heap/Index Scan하여 해시 집합으로 처리했습니다. `coupon_campaign_targets`는 계획에 전혀 나타나지 않아 대상 컬렉션을 읽지 않으며, 카드별 발급 쿼리가 없습니다. 실행시간은 1.248 ms였습니다.
- 지갑의 `ISSUED` 20건: `idx_member_coupons_member_status_valid_until_id` Bitmap Index Scan이 `(member_id, status, valid_until)` 조건에 사용됐습니다. 정렬/조인은 한 번 수행되며 페이지 행마다 캠페인이나 대상 조회가 생기지 않았습니다. 실행시간은 14.985 ms였습니다.
- 소유자·기간 캠페인 관리 조회: `idx_coupon_campaigns_owner_lifecycle_issue_period` Index Scan이 `(owner_type, store_id, lifecycle_status, issue_starts_at, issue_ends_at)`에 사용됐습니다. 실행시간은 0.665 ms였습니다.
- 대상 테이블에는 `idx_coupon_campaign_targets_product_campaign`이 유지됩니다. M26의 사용가능/지갑 목록은 대상 상세를 반환하지 않으므로 이 목록 경로에서 대상 조인이 필요하지 않습니다.

관측된 계획은 기존 인덱스를 사용했고 목록 쿼리는 목표 컬렉션을 읽지 않았으므로, M26에서 추가 인덱스는 넣지 않았습니다.

## 검증 결과

- 집중 범위(`coupon`, `promotion`, `cart`, `order`, `payment`): 169 tests, failures 0, errors 0, skipped 0. Gradle `BUILD SUCCESSFUL` (1m 06s).
- 전체 백엔드: 597 tests / 86 classes, failures 0, errors 0, skipped 0. `./gradlew.bat test --rerun-tasks`는 `BUILD SUCCESSFUL` (2m 57s)입니다.
- 웹: `npm run build` 종료코드 0. Vite가 518.00 kB JavaScript 청크 경고를 출력하지만 빌드는 성공했습니다.

## 후속 마일스톤

- M27: 캠페인별 발급 수량/재고(용량)와 동시성 정책을 도입합니다.
- M28: 주문 가격에 쿠폰을 실제로 적용하고, 사용 처리·취소/환불 복구·프로모션과의 중첩 정책을 구현합니다.
