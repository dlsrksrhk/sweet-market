# Sweet Market Learning Test Guide

이 문서는 Sweet Market 프로젝트의 테스트를 통해 학습할 내용을 목차별로 정리한다.

프로젝트의 테스트는 단순한 회귀 검증을 넘어서 JPA, 트랜잭션, 인증/인가, 도메인 상태 전이, 조회 최적화, Spring Batch, 웹 데모 데이터까지 단계적으로 학습하도록 구성되어 있다.

## 1. 테스트 환경과 실행 규칙

### 학습 목표

- 통합 테스트가 실제 PostgreSQL에 가까운 환경에서 어떻게 실행되는지 이해한다.
- 테스트 격리, 공통 fixture, 동적 property 주입 방식을 익힌다.
- 프로젝트의 테스트 이름 규칙을 지킨다.

### 관련 테스트와 파일

- `backend/src/test/java/com/sweet/market/support/IntegrationTestSupport.java`
- `backend/src/test/java/com/sweet/market/MarketApplicationTests.java`
- `AGENTS.md`

### 핵심 시나리오

- Testcontainers PostgreSQL을 모든 통합 테스트의 공통 DB로 사용한다.
- 각 테스트 후 주요 테이블을 truncate해서 테스트 간 데이터를 격리한다.
- 테스트 환경에서는 Spring Batch metadata schema를 컨테이너 초기화 스크립트로 준비한다.
- `jwt.secret`, datasource, JPA ddl-auto 등 테스트 전용 property를 동적으로 주입한다.

### 실무적으로 기억할 점

- 통합 테스트는 H2 같은 대체 DB보다 실제 운영 DB에 가까운 PostgreSQL에서 검증하는 편이 안전하다.
- 테스트 격리는 테스트 신뢰도의 핵심이다.
- JUnit `@Test` 메서드명은 Korean_with_underscores 형식으로 작성한다.

## 2. 인증과 보안

### 학습 목표

- 회원가입/로그인 흐름과 JWT 기반 인증 구조를 이해한다.
- Spring Security에서 인증 실패, 권한 실패, role 기반 접근 제어를 구분한다.
- BCrypt 비밀번호 제한 같은 라이브러리 제약을 API 검증으로 끌어올리는 방법을 익힌다.

### 관련 테스트

- `AuthApiTest`
- `SecurityApiTest`
- `AdminSecurityApiTest`
- `BcryptPasswordValidatorTest`
- `MarketApplicationTests`

### 핵심 시나리오

- 회원가입 성공과 중복 이메일 실패를 검증한다.
- 이메일 정규화 후 로그인할 수 있는지 확인한다.
- JWT가 없거나 유효하지 않으면 보호 API 접근이 실패한다.
- JWT에 role claim이 없으면 인증을 거부한다.
- ADMIN 권한이 없는 사용자는 `/api/admin/**`에 접근할 수 없다.
- BCrypt 72바이트 제한을 초과하는 비밀번호는 검증 단계에서 거부한다.

### 실무적으로 기억할 점

- 인증 실패는 401, 인가 실패는 403으로 분리해서 다루는 것이 좋다.
- JWT claim은 누락되거나 변조될 수 있으므로 방어적으로 파싱해야 한다.
- 비밀번호 정책은 DB나 암호화 라이브러리 제약까지 고려해야 한다.

## 3. JPA 기본 동작

### 학습 목표

- JPA의 변경 감지, cascade, orphanRemoval, optimistic locking을 실제 SQL과 함께 이해한다.
- 도메인 모델 변경이 영속성 컨텍스트와 flush 시점에 어떻게 반영되는지 관찰한다.

### 관련 테스트

- `DirtyCheckingTest`
- `CascadeOrphanRemovalTest`
- `OptimisticLockTest`
- `OrderRepositoryTest`

### 핵심 시나리오

- 주문 취소 시 별도 save 호출 없이 dirty checking으로 주문과 상품 상태 변경이 update 되는지 확인한다.
- 상품 저장 시 상품 이미지가 cascade persist 되는지 확인한다.
- 상품 이미지 컬렉션에서 제거된 이미지가 orphanRemoval로 삭제되는지 확인한다.
- 같은 상품을 두 트랜잭션이 동시에 예약할 때 나중 커밋이 optimistic lock으로 실패하는지 검증한다.
- 취소된 주문이 있어도 같은 상품으로 새 주문을 저장할 수 있는지 확인한다.

### 실무적으로 기억할 점

- JPA의 편리함은 flush 시점과 영속성 컨텍스트를 이해할 때 안전해진다.
- `@OneToOne(unique = true)` 같은 매핑 결정은 비즈니스 이력 모델과 충돌할 수 있다.
- 동시성 문제는 단위 테스트보다 실제 트랜잭션을 나눈 통합 테스트로 검증하는 편이 명확하다.

## 4. 상품 도메인

### 학습 목표

- 상품 aggregate가 상태와 이미지 생명주기를 어떻게 소유하는지 이해한다.
- 판매자 권한, 상품 공개 여부, 예약 상태 보호 규칙을 API와 도메인 테스트로 검증한다.

### 관련 테스트

- `ProductTest`
- `ProductApiTest`
- `ProductSellerApiTest`
- `ProductQueryOptimizationTest`

### 핵심 시나리오

- 상품을 이미지와 함께 생성한다.
- 상품 제목, 설명, 가격을 수정한다.
- 상품을 숨김 처리한다.
- 판매중 상품만 예약할 수 있다.
- 예약 상품은 판매중으로 복구할 수 있다.
- 예약 상품은 판매완료로 전이할 수 있다.
- 예약 상품은 수정하거나 숨길 수 없다.
- 공개 상품 목록과 상세 조회에서 숨김 상품을 제외한다.
- 판매자는 자신의 판매 상품 목록을 조회한다.

### 실무적으로 기억할 점

- 상태 전이는 service가 임의로 값을 바꾸기보다 도메인 메서드가 소유하는 편이 안전하다.
- 예약 중인 상품을 판매자가 수정하거나 숨기면 주문 취소/배송 흐름이 깨질 수 있으므로 보호 규칙이 필요하다.
- 판매자 전용 조회와 공개 조회는 요구사항과 성능 특성이 다르다.

## 5. 주문 도메인

### 학습 목표

- 주문 생성/취소가 상품 상태와 함께 하나의 거래 예약 흐름을 만든다는 점을 이해한다.
- 주문 상태 전이를 도메인 규칙으로 제한하는 방법을 익힌다.

### 관련 테스트

- `OrderTest`
- `OrderApiTest`
- `OrderConfirmApiTest`
- `OrderQueryApiTest`
- `OrderRepositoryTest`
- `OrderQueryOptimizationTest`

### 핵심 시나리오

- 주문 생성 시 상품이 `ON_SALE`에서 `RESERVED`로 전이된다.
- 주문 취소 시 상품이 다시 `ON_SALE`로 복구된다.
- 이미 취소한 주문은 다시 취소할 수 없다.
- 결제완료, 배송중, 배송완료, 구매확정 상태로 순차 전이한다.
- 배송완료 주문만 구매확정할 수 있다.
- 구매확정 시 `confirmedAt`을 기록한다.
- 구매자는 자신의 주문 목록과 상세만 조회할 수 있다.

### 실무적으로 기억할 점

- 주문과 상품 상태는 함께 움직이므로 aggregate 경계와 트랜잭션 경계가 중요하다.
- 상태 전이는 “가능한 이전 상태”를 명시해야 예외 케이스가 줄어든다.
- 조회 API는 권한 필터링과 fetch 전략을 함께 검토해야 한다.

## 6. 결제와 배송

### 학습 목표

- 외부 연동 경계를 fake adapter로 먼저 설계하는 방법을 이해한다.
- 결제/배송 도메인이 주문 상태 전이를 어떻게 이어받는지 익힌다.

### 관련 테스트

- `PaymentTest`
- `PaymentApiTest`
- `DeliveryTest`
- `DeliveryApiTest`

### 핵심 시나리오

- 주문자가 결제 승인에 성공하면 주문이 `PAID`가 된다.
- 주문자가 아니면 결제 승인에 실패한다.
- 이미 승인된 주문은 다시 결제 승인할 수 없다.
- 승인된 결제만 취소할 수 있다.
- 결제 취소 시 주문과 상품이 취소/판매중 상태로 복구된다.
- 결제된 주문만 배송을 시작할 수 있다.
- 배송 시작 시 주문이 `SHIPPING`이 된다.
- 배송 완료 시 주문이 `DELIVERED`가 된다.

### 실무적으로 기억할 점

- 실제 결제사/배송사 연동 전에 fake 경계로 트랜잭션과 실패 처리를 먼저 학습하는 것이 좋다.
- 외부 연동 결과와 내부 도메인 상태 전이를 한 트랜잭션에서 어떻게 조율할지 결정해야 한다.

## 7. 구매 확정과 정산

### 학습 목표

- 거래 완료 후 판매자 정산이 생성되는 흐름을 이해한다.
- 중복 정산 방지와 판매자별 정산 조회 규칙을 검증한다.

### 관련 테스트

- `SettlementTest`
- `SettlementApiTest`
- `OrderConfirmApiTest`
- `SettlementQueryOptimizationTest`

### 핵심 시나리오

- 확정된 주문으로 정산을 생성한다.
- 확정되지 않은 주문은 정산할 수 없다.
- 같은 주문은 중복 정산할 수 없다.
- 판매자가 아니면 정산 생성에 실패한다.
- 판매자는 자신의 정산 목록만 조회한다.
- 구매 확정 시 상품은 판매완료 상태로 전이된다.

### 실무적으로 기억할 점

- 정산은 돈과 관련된 데이터이므로 idempotency와 unique constraint가 중요하다.
- 도메인 규칙과 DB 제약을 함께 사용해야 중복 생성 위험을 줄일 수 있다.
- 정산 조회는 order/product/seller 연관 로딩 전략이 바로 성능 문제로 이어진다.

## 8. 조회 최적화

### 학습 목표

- N+1 문제를 재현하고, 필요한 연관만 로딩하는 최적화 전략을 익힌다.
- EntityGraph와 projection 성격의 응답 조립을 비교하며 JPA 조회 설계를 학습한다.

### 관련 테스트

- `ProductQueryOptimizationTest`
- `OrderQueryOptimizationTest`
- `SettlementQueryOptimizationTest`
- `QueryOptimizationTestSupport`

### 핵심 시나리오

- 상품 목록 단순 조회에서 seller N+1이 발생하는지 확인한다.
- 상품 목록 최적화 조회는 seller를 함께 로딩하고 images는 로딩하지 않는다.
- 주문 목록 단순 조회에서 product/seller N+1이 발생하는지 확인한다.
- 주문 목록 최적화 조회는 product/seller를 함께 로딩한다.
- 정산 목록 단순 조회에서 주문/상품 N+1이 발생하는지 확인한다.
- 정산 목록 최적화 조회는 한 번의 쿼리로 필요한 응답을 만든다.
- 정산 목록 조회는 필요한 graph만 선언한다.

### 실무적으로 기억할 점

- “연관을 많이 eager로 당겨오면 안전하다”가 아니라 “화면에 필요한 만큼만 명시적으로 가져온다”가 핵심이다.
- pagination이 필요한 목록에서 fetch join을 남용하면 다른 문제가 생길 수 있다.
- 쿼리 수 검증 테스트는 성능 회귀를 잡는 좋은 안전망이다.

## 9. Spring Batch

### 학습 목표

- Spring Batch의 job, step, reader, processor, writer 구조를 이해한다.
- 대량 정산 생성에서 retry, skip, idempotency를 어떻게 설계하는지 익힌다.
- Batch metadata를 운영 API에서 읽는 방법을 학습한다.

### 관련 테스트

- `SettlementBatchJobTest`
- `AdminSettlementBatchApiTest`
- `AdminSettlementBatchHistoryApiTest`
- `AdminSecurityApiTest`

### 핵심 시나리오

- 기준 시각 이전에 확정된 미정산 주문만 정산한다.
- 기준 시각 이후 확정 주문은 정산하지 않는다.
- 같은 조건으로 다시 실행해도 중복 정산하지 않는다.
- reader 이후 이미 정산된 주문은 skip 처리한다.
- writer 시점 중복 정산은 skippable 예외로 분류한다.
- 관리자만 정산 배치를 실행할 수 있다.
- batch launch 실패는 구조화된 오류 응답을 반환한다.
- 관리자는 정산 배치 실행 목록과 상세를 조회할 수 있다.
- 일반 회원은 batch history API에 접근할 수 없다.

### 실무적으로 기억할 점

- batch는 “한 번 성공”보다 “다시 실행해도 안전한가”가 더 중요하다.
- DB unique constraint, `on conflict do nothing`, skippable exception을 조합해 중복 생성 위험을 줄인다.
- Spring Batch metadata는 도메인 entity가 아니라 인프라 데이터로 보고 `JdbcTemplate`로 조회한다.

## 10. 데모 데이터와 웹 연동

### 학습 목표

- 로컬 웹 데모를 위한 seed data를 profile 기반으로 안전하게 주입하는 방법을 익힌다.
- 프론트엔드가 실제 API 상태 전이를 따라갈 수 있도록 충분한 샘플 상태를 준비한다.

### 관련 테스트와 파일

- `DemoDataInitializer`
- `DemoDataInitializerTest`
- `docs/superpowers/handoffs/2026-06-13-milestone-8-web-demo-handoff.md`

### 핵심 시나리오

- `local` 또는 `dev` profile에서만 demo seed가 실행된다.
- `admin@example.com`이 이미 있으면 재시드하지 않는다.
- 반복 실행해도 demo 데이터가 중복 생성되지 않는다.
- admin, seller, buyer 계정을 생성한다.
- CREATED, PAID, SHIPPING, DELIVERED, CONFIRMED 미정산, CONFIRMED 정산완료 상태 데이터를 만든다.

### 실무적으로 기억할 점

- demo seed는 운영 데이터와 절대 섞이면 안 되므로 profile 조건이 필요하다.
- seed는 idempotent해야 로컬 서버를 여러 번 켜도 데이터가 망가지지 않는다.
- 웹 데모는 “보기 좋은 데이터”보다 “상태 전이를 학습할 수 있는 데이터”가 중요하다.

## 11. 웹 애플리케이션 연동 검증

### 학습 목표

- 백엔드 API가 실제 화면 흐름에서 어떻게 소비되는지 이해한다.
- 인증 토큰, route guard, server state invalidation, error normalization을 학습한다.

### 관련 파일

- `web/src/shared/api/http.ts`
- `web/src/features/auth/AuthProvider.tsx`
- `web/src/app/router.tsx`
- `web/src/pages/HomePage.tsx`
- `web/src/pages/ProductDetailPage.tsx`
- `web/src/pages/MyOrdersPage.tsx`
- `web/src/pages/MySalesPage.tsx`
- `web/src/pages/MySettlementsPage.tsx`
- `web/src/pages/AdminSettlementBatchPage.tsx`

### 핵심 시나리오

- 사용자는 상품을 둘러보고 로그인할 수 있다.
- 구매자는 상품 상세에서 주문을 생성하고 거래 상태를 진행한다.
- 판매자는 자신의 판매 상품과 정산을 확인한다.
- 관리자는 정산 배치를 실행하고 이력을 확인한다.
- API 오류는 화면에서 code/message 기반으로 확인할 수 있다.

### 실무적으로 기억할 점

- 프론트엔드는 백엔드 도메인 상태 전이를 드러내는 좋은 학습 도구다.
- route guard는 보안의 대체물이 아니라 사용자 경험 계층이다. 실제 권한 검증은 백엔드가 담당해야 한다.
- mutation 이후 관련 query invalidation을 놓치면 화면 상태가 쉽게 낡는다.

## 12. 학습 순서 제안

처음부터 모든 테스트를 한꺼번에 읽기보다 아래 순서로 따라가면 이해가 쉽다.

1. `MemberTest`, `AuthApiTest`, `SecurityApiTest`
2. `ProductTest`, `ProductApiTest`
3. `OrderTest`, `OrderApiTest`, `OrderRepositoryTest`
4. `PaymentTest`, `PaymentApiTest`
5. `DeliveryTest`, `DeliveryApiTest`
6. `OrderConfirmApiTest`, `SettlementTest`, `SettlementApiTest`
7. `DirtyCheckingTest`, `CascadeOrphanRemovalTest`, `OptimisticLockTest`
8. `ProductQueryOptimizationTest`, `OrderQueryOptimizationTest`, `SettlementQueryOptimizationTest`
9. `SettlementBatchJobTest`, `AdminSettlementBatchApiTest`, `AdminSettlementBatchHistoryApiTest`
10. `DemoDataInitializerTest`
11. 웹 화면을 실행해서 API 흐름을 수동으로 확인한다.

## 13. 실행 명령 모음

### 전체 백엔드 테스트

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test
```

### JPA 학습 테스트만 실행

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.jpalab.*
```

### Spring Batch 테스트만 실행

```powershell
cd backend
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.settlement.batch.*
```

### 웹 빌드 검증

```powershell
cd web
npm install
npm run build
```

### 로컬 데모 실행

```powershell
cd backend
docker compose up -d
$env:JAVA_HOME='C:\java\jdk-21'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
$env:SPRING_PROFILES_ACTIVE='local'
.\gradlew.bat bootRun
```

```powershell
cd web
npm run dev
```

브라우저에서 `http://localhost:5173`을 연다.

Demo 계정 비밀번호는 모두 `password123`이다.

