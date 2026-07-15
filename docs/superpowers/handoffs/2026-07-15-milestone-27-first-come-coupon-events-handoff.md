# Milestone 27: 선착순 쿠폰 이벤트 인수인계

## 범위와 기준점

- 브랜치: `codex/milestone-27-first-come-coupon-events`
- M27 기능 최종 커밋: `5a782c3 test: expect coupon issue limit migration`
- DB 마이그레이션: `backend/src/main/resources/db/migration/V11__add_coupon_campaign_issue_limits.sql`

M27은 쿠폰 캠페인별 선택적 발급 한도와 선착순 발급 제어, 운영자 현황 및 구매자 마감 표시를 제공합니다.

## Redis 발급 제어와 장애 시 보장

- 설정: `REDIS_HOST`(기본 `localhost`), `REDIS_PORT`(기본 `6379`)는 `backend/src/main/resources/application.yaml`의 `spring.data.redis`에 연결됩니다.
- 정상 경로: `RedisCouponIssuanceGate`가 `classpath:redis/coupon-reserve.lua`로 한도 예약을 수행하고, DB 확정 뒤 `redis/coupon-complete.lua`로 확정합니다. DB 확정 실패 또는 중복 처리 중에는 `redis/coupon-release.lua`로 예약을 반환합니다.
- 키 형식: `coupon:issue:{campaignId}:count`, `pending`, `member:{memberId}`입니다. 예약은 30초이며, 캐시 TTL은 발급 종료 시각 뒤 1분의 회복 여유를 포함합니다.
- Redis 장애 대체: 제한 캠페인에서 Redis 접근이 `CouponIssuanceGateUnavailableException`을 내면 `CouponIssueTransactionService.issueWithPessimisticLock`으로 전환합니다. 따라서 Redis를 사용할 수 없어도 DB의 비관적 잠금과 한도 확인으로 초과 발급을 막습니다.

## 검증 결과

### 집중 백엔드 회귀 테스트: 통과

다음 명령은 설치된 JDK 21 (`C:\Users\kdh\.jdks\corretto-21.0.7`)으로 실행했고 종료 코드 0, `BUILD SUCCESSFUL`을 확인했습니다. 지정 예시의 `C:\java\jdk-21`은 이 환경에 존재하지 않아 사용할 수 없었습니다.

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.coupon.domain.CouponCampaignTest' --tests 'com.sweet.market.coupon.RedisCouponIssuanceGateTest' --tests 'com.sweet.market.coupon.CouponIssueApiTest' --tests 'com.sweet.market.coupon.CouponWalletApiTest' --tests 'com.sweet.market.coupon.CouponQueryOptimizationTest'
```

결과: 1분 6초, `BUILD SUCCESSFUL`.

### 전체 백엔드 테스트: 통과

집중 테스트 이후 Docker Desktop과 Testcontainers를 준비한 뒤 전체 테스트를 다시 실행했습니다. M27 작업 브랜치와 병합된 `main` 모두에서 종료 코드 0과 `BUILD SUCCESSFUL`을 확인했습니다.

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test
```

병합된 `main`의 최종 실행 결과: 3분 14초, `BUILD SUCCESSFUL`.

### 웹 프로덕션 빌드: 통과

```powershell
cd web
npm run build
```

결과: 종료 코드 0, Vite 빌드 완료(1.77초). 번들 `index-IwhFaPM3.js`는 압축 전 500 kB 경고가 있으나 빌드 실패는 아닙니다.

## Docker 및 로컬 서버 상태

- 최초 `docker info`는 `dockerDesktopLinuxEngine` named pipe 부재로 실패했습니다.
- Docker Desktop은 `C:\Program Files\Docker\Docker\Docker Desktop.exe`에 설치되어 있었고, 숨김 창으로 한 번 시작한 뒤 약 11초 내 `docker info`가 성공했습니다(Docker Server 28.1.1, `desktop-linux`).
- 브라우저 QA용으로 `backend/docker-compose.yml`의 PostgreSQL을 `15432`에 기동했고, `m27-local-redis`(`redis:7.4-alpine`)를 `6379`에 임시 기동했습니다.
- 백엔드 `bootRun` 로그는 Tomcat 8080 및 `MarketApplication` 시작을 확인했습니다(약 12초). 웹 Vite 개발 서버는 `http://127.0.0.1:5173`에서 시작했습니다.

## Task 5 브라우저 QA: 실행 차단

백엔드와 웹 개발 서버가 기동된 뒤에도 headless browse 데몬은 네 차례 모두 15초 안에 서버를 시작하지 못했습니다. 총 대기 60초 후 중단했으므로 아래 시나리오를 실제 브라우저로 검증하지 못했습니다.

1. 빈 한도 입력이 `undefined`로 직렬화되고 운영자 화면에서 `발급 무제한`으로 표시되는지.
2. 한도 3인 초안이 `발급 0 / 3`, `잔여 3`으로 표시되는지.
3. 소진된 구매자 카드가 `선착순 마감`과 비활성화된 버튼을 표시하는지.
4. 이미 발급한 소진 카드가 마감 행동 대신 `발급 완료`를 표시하는지.

차단 원인은 애플리케이션이 아니라 로컬 browser daemon 기동 실패입니다. 다음 실행에서는 browser 도구를 복구한 뒤, 한도 3 캠페인과 두 구매자 계정으로 위 네 항목을 확인해야 합니다.

## M28 경계

쿠폰 **사용/교환**과 주문 가격 변경은 M27에 포함되지 않았습니다. M28에서 쿠폰 적용, 주문 가격 계산 및 해당 취소·환불 영향 범위를 별도로 설계하고 구현해야 합니다.
