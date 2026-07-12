# M23 Task 3 구현 보고서

## 상태

- 완료: 운영자 재고 수동 조정 API와 페이지형 이력 조회 API
- 브랜치: `codex/milestone-23-inventory`
- 작업 트리: `C:\dev\study\sweet-market\.worktrees\codex-milestone-23-inventory`
- 범위 외 `package-lock.json`은 untracked 상태 그대로 보존

## 구현 내용

- `PATCH /api/store-operations/{storeId}/products/{productId}/inventory`
  - 활성 OWNER/MANAGER만 허용 (`requireCatalogOperator`)
  - 결과 총수량, 표준 사유, 선택적 참조 메모 검증
  - 예약량보다 낮은 결과 총수량을 `INVENTORY_ADJUSTMENT_CONFLICT`(409)로 거부
  - 재고를 `@Version` 기반 낙관적 잠금으로 조회하고 flush 시 버전 충돌을 409로 변환
  - 재고 변경과 `MANUAL_ADJUSTMENT` 감사 레코드를 한 트랜잭션에 저장
- `GET /api/store-operations/{storeId}/products/{productId}/inventory/history?page=&size=`
  - 활성 멤버십의 OWNER/MANAGER만 허용 (`requireOperator`)
  - 정지 상점 운영자에게 읽기 허용, 쓰기는 거부
  - 발생 시각과 ID 역순의 결정적 페이지네이션, 기본 0/20, 최대 크기 100
  - 변경 전후 총수량/예약량, 사유, 참조 메모, 담당 운영자, 발생 시각 반환
- 이력 수정/삭제 엔드포인트는 추가하지 않음

## TDD 증거

### RED

명령:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.store.StoreOperationsApiTest'
```

결과: `BUILD FAILED`, 21개 중 새 테스트 4개 실패. 기존 엔드포인트가 없어 OWNER/MANAGER 성공, 외부인 거부, 정지 상점 읽기 전용, 예약량 충돌 테스트가 모두 예상대로 실패함.

### GREEN

같은 명령을 구현 후 다시 실행한 결과: `BUILD SUCCESSFUL in 37s`, exit code 0.

## 최종 검증

명령:

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.inventory.*' --tests 'com.sweet.market.store.StoreOperationsApiTest'
```

결과: `BUILD SUCCESSFUL in 30s`, exit code 0. XML 결과 합계는 28 tests, 0 failures, 0 errors, 0 skipped.

추가 점검:

```powershell
git diff --check
```

결과: exit code 0.

## 테스트 범위

- OWNER와 MANAGER의 재고 조정 및 최신순 감사 이력 조회
- 외부 회원의 조정/조회 거부
- 정지 상점 운영자의 조회 허용과 조정 거부
- 예약량 미만 결과 총수량의 409 응답과 부분 감사 레코드 미생성
- 기존 재고 도메인 불변식 회귀
