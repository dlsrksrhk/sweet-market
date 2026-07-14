# Task 2: Business-owner campaign management API 보고서

## 완료 범위

- 활성 `BUSINESS` 상점의 실제 `OWNER`만 프로모션을 관리하도록 `StoreAccessService.requireActiveBusinessOwner`를 추가했습니다. 개인 상점, 비활성 사업자 상점, 매니저 및 외부 회원은 차단됩니다.
- `/api/stores/{storeId}/promotions`의 생성·목록·상세·수정과 `schedule`, `pause`, `resume`, `end` 생명주기 API를 구현했습니다.
- KST `LocalDateTime` 요청값을 `Asia/Seoul` 기준 UTC `Instant`로 저장하고, 응답에는 KST 시간, 설정/유효 상태, 범위, 대상 수, 할인 규칙, 우선순위, 구매자 표시 문구를 반환합니다.
- 선택상품은 비어 있지 않은 고유 ID만 받으며 해당 상점의 구매 가능한 상품인지 검증합니다. 상점 전체 범위는 상품 ID를 받지 않습니다.
- 상태 및 기간 필터와 0 이상 페이지, 1~100 크기 제한을 적용했습니다. 만료된 일시정지 캠페인은 `PAUSED` 필터에만 나타나도록 유효 상태와 조회 조건을 맞췄습니다.
- 존재하지 않거나 다른 상점에 속한 캠페인은 `PROMOTION_NOT_FOUND`(404), 허용되지 않는 생명주기 전이는 `PROMOTION_LIFECYCLE_NOT_ALLOWED`(409)로 응답합니다.
- 대상 변경 시 기존 대상과 새 대상을 차분 반영하도록 집계 루트를 보정해, 같은 상품을 유지하는 수정에서 유니크 제약 위반이 발생하지 않게 했습니다.

## TDD 및 검증

1. `PromotionCampaignApiTest`를 먼저 추가하고 API 부재로 10개 테스트가 404 실패하는 RED 상태를 확인했습니다.
2. 구현 후 JPA 파라미터/상태 필터 오류를 집중 테스트로 수정했습니다.
3. 기간 역전 검증과 캠페인 미존재 404는 각각 실패 테스트를 먼저 확인한 뒤 보완했습니다.
4. 최종 실행 명령:

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.promotion.PromotionCampaignApiTest' --tests 'com.sweet.market.promotion.domain.PromotionCampaignTest'
```

결과: `BUILD SUCCESSFUL`. API 테스트 15개와 기존 프로모션 도메인 테스트를 통과했습니다.

## 자체·독립 검토

- `git diff --check` 통과했습니다.
- 독립 리뷰에서 확인된 만료된 일시정지 캠페인의 `ENDED` 필터 중복, 존재하지 않는 캠페인의 400 응답, 고정 미래 시각 테스트 문제를 모두 반영했습니다.
- 전체 백엔드 테스트는 실행하지 않았습니다. 이전 작업에서 공유 PostgreSQL Testcontainer 연결 한도 문제가 보고되어, 본 작업은 요청 범위의 집중 테스트로 검증했습니다.

## 변경 파일

- `backend/src/main/java/com/sweet/market/promotion/api/*`
- `backend/src/main/java/com/sweet/market/promotion/application/PromotionCampaignService.java`
- `backend/src/main/java/com/sweet/market/promotion/repository/PromotionCampaignRepository.java`
- `backend/src/main/java/com/sweet/market/promotion/domain/PromotionCampaign.java`
- `backend/src/main/java/com/sweet/market/store/application/StoreAccessService.java`
- `backend/src/main/java/com/sweet/market/common/error/ErrorCode.java`
- `backend/src/test/java/com/sweet/market/promotion/PromotionCampaignApiTest.java`
