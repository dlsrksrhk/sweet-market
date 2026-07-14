# Task 5 Report

## 변경 사항

- 카탈로그 단일 JDBC 투영에서 선택 상품·상점 전체의 유효 프로모션 하나를 실판매가, 우선순위, ID 순으로 선택합니다.
- 최소/최대 가격 필터, 가격 정렬, 키셋 커서가 실판매가를 사용하도록 변경했습니다.
- 구매자 카드에 `listPrice`, 프로모션 정보, `effectivePrice`를 추가했습니다.
- 익명 페이지의 단일 투영과 인증 사용자별 찜·장바구니 일괄 조회 구조를 유지했습니다.

## TDD 및 검증

- 구현 전에 실판매가 필터·정렬·동점 경계·선택/상점 전체 프로모션 및 카드 응답을 검증하는 테스트를 추가했고, 필요한 투영 API가 없어 컴파일 실패하는 것을 확인했습니다.
- JDK 21과 `JWT_SECRET` 환경에서 `CatalogApiTest`, `CatalogSearchRepositoryTest`, `CatalogCursorCodecTest`, `CatalogQueryOptimizationTest`를 실행해 통과했습니다.
