# Post-Milestone 26 Next Session Handoff

## 현재 상태

- `main`은 Milestone 26 구현 병합 커밋 `39a00aa`까지 원격 `origin/main`에 반영되어 있다.
- M26은 플랫폼/상점 쿠폰 캠페인, 전체·선택 상품 적용 범위, 선택 상품의 다중 상점 혼합 지정, 사용자 쿠폰 지갑, 발급 멱등성, 관리자·구매자 UI를 포함해 완료되었다.
- 상세 구현 내용은 [M26 implementation handoff](2026-07-14-milestone-26-coupon-campaigns-and-standard-coupon-issuance-handoff.md)를 참고한다.

## 검증 상태

- M26 작업 브랜치에서 백엔드 전체 테스트 601개와 웹 빌드가 통과했다.
- 병합 후 `main`에서 웹 빌드와 변경 사항 검사는 통과했다.
- 병합 후 백엔드 전체 테스트 재실행은 Docker Desktop 데몬이 실행되지 않아 Testcontainers 초기화 단계에서만 실패했다. 코드 또는 테스트 회귀로 판정하지 않았으며, Docker Desktop 실행 후 재확인하면 된다.

## 다음 권장 작업: Milestone 27

M27은 선착순 쿠폰 이벤트와 동시 발급 제어를 다룬다.

- 캠페인별 발급 한도와 현재 발급 수를 모델링한다.
- 동시 요청에서 한도를 넘겨 발급하지 않도록 DB 락, 원자적 갱신 또는 예약 전략을 설계한다.
- 이미 발급받은 사용자의 재시도는 M26의 멱등성 규칙을 유지한다.
- 동시성·한도 경계 테스트를 우선 작성한다.
- 쿠폰 사용(주문 할인 적용)은 M28 범위로 남긴다. 현재 주문은 단일 상품 단위라는 제약도 유지한다.

## 시작 시 유의 사항

- 작업은 새 worktree와 `codex/milestone-27-first-come-coupon-events` 브랜치에서 시작한다.
- 구현 전 brainstorming과 설계 문서·실행 계획을 먼저 확정한다.
- 백엔드 테스트는 JDK 21과 `JWT_SECRET` 환경 변수를 사용한다. 현재 로컬에는 `C:\Users\kdh\.jdks\corretto-21.0.7`이 설치되어 있다.
- 다음 로컬 전용 변경은 사용자 작업이므로 보존한다.
  - `backend/src/main/resources/application.yaml`
  - `docs/superpowers/handoffs/2026-07-08-post-milestone-18-next-session-handoff.md`
