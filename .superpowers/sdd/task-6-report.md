# Task 6 Report: Promotion workspace and buyer prices

## 구현

- 프로모션 조회·상세·생성·수정·상태 전환 API 계약과 React Query 키를 추가했습니다.
- 인증 경로 `/me/store/promotions`, `/me/store/promotions/:storeId/:promotionId`를 추가하고, 활성 사업자 상점 OWNER만 생성·수정·예약·중지·재개·종료할 수 있는 화면을 제공했습니다. 개인 상점과 매니저에게는 명확한 접근 불가 상태를 표시합니다.
- 내 상점 소유자 메뉴에 프로모션 링크를 추가했습니다.
- 카탈로그, 기존 상품/찜 카드, 상품 상세, 장바구니가 공통 가격 표시를 사용하도록 갱신했습니다. 프로모션이 있을 때만 정가·할인·실판매가를 함께 표시하며 장바구니 합계는 `effectivePrice`를 합산하고 주문 시 확정됨을 안내합니다.
- 프로모션 변경 뒤 프로모션·카탈로그·상품·장바구니 쿼리를 무효화하며, 일시 입력 및 출력은 백엔드 KST 계약을 유지합니다.

## 검증

- `web`: 의존성 설치 후 `npm run build` 통과했습니다.
- 기존 웹 프로젝트에는 별도 프런트엔드 테스트 실행기가 없어 TypeScript 정적 검사와 Vite 프로덕션 빌드를 검증 수단으로 사용했습니다.
- `git diff --check -- web` 통과했습니다.
