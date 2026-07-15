# Task 6 Report: M27 호환성 검증 및 인수인계

## 완료 사항

- 인수인계 문서를 `docs/superpowers/handoffs/2026-07-15-milestone-27-first-come-coupon-events-handoff.md`에 작성했습니다.
- 문서 커밋: `abff6bb docs: hand off milestone 27 coupon events`
- M27 기능 기준 커밋: `57bbe31 feat: show first-come coupon event status`
- 브랜치: `codex/milestone-27-first-come-coupon-events`

## 검증 증거

### 통과

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests 'com.sweet.market.coupon.domain.CouponCampaignTest' --tests 'com.sweet.market.coupon.RedisCouponIssuanceGateTest' --tests 'com.sweet.market.coupon.CouponIssueApiTest' --tests 'com.sweet.market.coupon.CouponWalletApiTest' --tests 'com.sweet.market.coupon.CouponQueryOptimizationTest'
```

- 집중 쿠폰 테스트: 종료 코드 0, `BUILD SUCCESSFUL in 1m 6s`.

```powershell
cd web
npm run build
```

- 웹 빌드: 종료 코드 0, `✓ built in 1.77s`.

### 미완료 또는 차단

- 전체 백엔드 `gradlew.bat test`는 Docker/Testcontainers가 가동된 상태에서 시작했으나 60초 안에 완료되지 않아 대기 한도에 따라 중단했습니다. 실패 또는 제품 회귀로 판정하지 않았습니다.
- 지정된 `C:\java\jdk-21`는 존재하지 않았습니다. 설치된 JDK 21인 `C:\Users\kdh\.jdks\corretto-21.0.7`으로 위 검증을 실행했습니다.
- `docker info`는 처음 `dockerDesktopLinuxEngine` pipe 부재로 실패했습니다. `C:\Program Files\Docker\Docker\Docker Desktop.exe`를 숨김으로 한 번 시작한 뒤 약 11초 내 성공했습니다(Server 28.1.1, `desktop-linux`).
- Task 5 브라우저 QA는 Backend Tomcat 8080 및 Vite 5173 기동 뒤에도 browse daemon이 15초 내 시작하지 못한 일이 네 차례 반복되어, 총 60초 후 중단했습니다. 따라서 한도 없음 표시, 한도 3 카운트, 소진 버튼 비활성화, 발급완료 우선 표시는 실제 브라우저에서 검증되지 않았습니다.

## 로컬 실행 확인

- Compose PostgreSQL은 `15432`에서, 임시 `m27-local-redis`는 `6379`에서 기동했습니다.
- 백엔드 로그에 `Tomcat started on port 8080` 및 `Started MarketApplication in 12.002 seconds`가 기록됐습니다.
- Redis 설정은 `REDIS_HOST`/`REDIS_PORT`(기본 `localhost`/`6379`)이며 Lua 정상 경로는 `redis/coupon-reserve.lua` → DB 확정 → `redis/coupon-complete.lua`, 실패 시 `redis/coupon-release.lua`입니다.
- Redis 접근 불가 시 제한 발급은 DB 비관적 잠금 경로로 대체되어 한도를 보장합니다.

## 다음 작업 경계

M28에서만 쿠폰 사용/교환과 주문 가격 변경을 구현합니다. M27에는 포함하지 않았습니다.

## 작업 범위 밖 변경

`.gitignore`의 `.gstack/` 추가 변경은 이미 존재했으며 수정하거나 커밋하지 않았습니다.
