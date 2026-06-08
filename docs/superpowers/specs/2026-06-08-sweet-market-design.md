# Sweet Market 설계 문서

## 목적

Sweet Market은 Spring Boot, JPA, PostgreSQL을 실무형 중고거래 커머스 백엔드 안에서 깊게 학습하기 위한 프로젝트다. 단순 예제가 아니라 실제 API 서버처럼 동작하는 거래 흐름을 먼저 만들고, 그 흐름 안에서 JPA의 내부 동작과 성능 이슈를 단계적으로 파고든다.

첫 번째 핵심 거래 흐름은 다음과 같다.

```text
회원가입/로그인 -> 상품 등록 -> 주문 생성 -> 결제 승인 -> 배송 -> 구매 확정 -> 판매자 정산
```

장기적으로는 일별 정산, 자동 구매 확정, 오래된 상품 정리, 판매자 리포트 같은 대규모 배치성 작업까지 확장한다.

## 현재 프로젝트 상태

- 저장소 루트 기준 백엔드 위치: `backend`
- 문서 위치: `docs/superpowers/specs`
- 프레임워크: Spring Boot
- 빌드 도구: Gradle
- Java: Java 21 toolchain
- 데이터베이스: Docker Compose 기반 PostgreSQL
- PostgreSQL 호스트 포트: `15432`
- 컨테이너 내부 PostgreSQL 포트: `5432`
- 호스트 PC에 이미 설치된 PostgreSQL의 `5432` 포트와 충돌하지 않도록 `15432`를 사용한다.

## 아키텍처 접근

처음부터 모든 기능을 완벽하게 만들기보다, 얇은 전체 거래 흐름을 먼저 관통시킨 뒤 각 영역을 깊게 만든다.

초기 구현은 다음 전체 흐름이 API로 동작하는 것을 목표로 한다.

```text
회원가입/로그인 -> 상품 등록 -> 주문 생성 -> 결제 승인 -> 배송 시작/완료 -> 구매 확정 -> 정산 생성
```

패키지는 도메인 기준으로 나눈다.

```text
com.sweet.market
  auth
  member
  product
  order
  payment
  delivery
  settlement
  common
```

각 도메인은 필요에 따라 다음 구조를 따른다.

```text
api
application
domain
repository
query
```

쓰기 흐름은 도메인 모델 중심으로 둔다.

```text
Controller -> Application Service -> Entity/Repository
```

조회 흐름은 별도로 최적화한다.

```text
Controller -> Query Service -> Query Repository -> Response DTO
```

이 구조는 실무형 백엔드로 자연스럽게 동작하면서도 JPA의 장단점을 관찰하기 좋다. 쓰기 쪽에서는 aggregate, 상태 전이, 트랜잭션, dirty checking을 다루고, 조회 쪽에서는 N+1, fetch join, DTO projection, pagination, batch size를 실험한다.

## 인증

처음부터 실무형 JWT 인증을 사용한다.

초기 인증 범위는 다음과 같다.

- 회원가입
- 로그인
- 비밀번호 해싱
- JWT access token 발급
- 현재 인증 사용자를 해석하는 Spring Security filter
- 상품 등록, 주문 생성, 결제, 배송, 구매 확정, 정산 API 인증 처리

Spring Security는 나중에 붙이는 부가 기능이 아니라 초기 백엔드의 일부로 다룬다.

## 핵심 도메인 모델

초기 엔티티는 다음과 같다.

- `Member`
- `Product`
- `ProductImage`
- `Order`
- `Payment`
- `Delivery`
- `Settlement`

초기 관계는 다음과 같다.

```text
Member 1 - N Product
Member 1 - N Order
Product 1 - N ProductImage
Product 1 - 1 Order
Order 1 - 1 Payment
Order 1 - 1 Delivery
Order 1 - 1 Settlement
```

엔티티 연관관계는 특별한 이유가 없으면 lazy loading을 기본으로 한다.

초기 상태값은 다음과 같다.

```text
ProductStatus
- ON_SALE
- RESERVED
- SOLD_OUT
- HIDDEN

OrderStatus
- CREATED
- PAID
- SHIPPING
- DELIVERED
- CONFIRMED
- CANCELED

PaymentStatus
- READY
- APPROVED
- CANCELED
- FAILED

DeliveryStatus
- READY
- SHIPPING
- DELIVERED

SettlementStatus
- READY
- COMPLETED
- FAILED
```

`Order`를 거래 흐름의 중심으로 다룬다.

- 주문 생성 시 상품을 예약 상태로 바꾼다.
- 결제 승인 시 주문을 결제 완료 상태로 바꾼다.
- 배송 시작과 완료에 따라 주문 상태를 함께 바꾼다.
- 구매 확정 시 주문을 확정 상태로 바꾸고 상품을 판매 완료 상태로 바꾼다.
- 정산은 구매 확정된 주문을 기준으로 생성한다.

## 외부 연동 경계

결제와 배송은 처음부터 인터페이스 뒤에 둔다.

예시는 다음과 같다.

```text
PaymentGateway
DeliveryClient
```

첫 구현에서는 실제 결제사나 배송사 API를 호출하지 않는다. fake adapter를 사용해서 외부 연동 구조, 실패 케이스, 트랜잭션 결정을 먼저 학습한다. 이후 Toss Payments 같은 실제 결제 API나 배송 API로 교체할 수 있게 한다.

## 초기 API 흐름

인증:

```text
POST /api/auth/signup
POST /api/auth/login
```

상품:

```text
POST   /api/products
GET    /api/products
GET    /api/products/{productId}
PATCH  /api/products/{productId}
DELETE /api/products/{productId}
```

주문:

```text
POST /api/orders
GET  /api/orders/me
GET  /api/orders/{orderId}
POST /api/orders/{orderId}/cancel
POST /api/orders/{orderId}/confirm
```

결제:

```text
POST /api/payments/{orderId}/approve
POST /api/payments/{orderId}/cancel
```

배송:

```text
POST /api/deliveries/{orderId}/start
POST /api/deliveries/{orderId}/complete
```

정산:

```text
POST /api/settlements/orders/{orderId}
GET  /api/settlements/me
```

## 서비스 책임

`AuthService`

- 회원가입
- 비밀번호 해싱
- 로그인
- JWT 발급

`ProductService`

- 상품 등록
- 상품 수정
- 상품 삭제 또는 숨김
- 상품 이미지 추가/삭제
- 판매자 권한 검증

`OrderService`

- 주문 생성
- 주문 취소
- 구매 확정
- 주문 상태 변경의 주요 트랜잭션 경계 관리

`PaymentService`

- 결제 승인
- 결제 취소
- `PaymentGateway`를 통한 결제 요청
- 결제 결과에 따른 주문 상태 전이

`DeliveryService`

- 배송 시작
- 배송 완료
- `DeliveryClient`를 통한 배송 요청
- 배송 결과에 따른 주문 상태 전이

`SettlementService`

- 단건 주문 정산 생성
- 중복 정산 방지
- 판매자 정산 조회
- 향후 배치 정산으로 확장

조회 서비스:

- `ProductQueryService`
- `OrderQueryService`
- `SettlementQueryService`

조회 서비스는 엔티티를 그대로 반환하지 않고 응답 DTO를 반환한다.

## 예외 처리

공통 에러 응답 구조와 전역 예외 처리기를 둔다.

초기 에러 범주는 다음과 같다.

- 검증 실패
- 인증 실패
- 권한 없음
- 엔티티 없음
- 잘못된 상태 전이
- 중복 비즈니스 액션
- 외부 adapter 실패

도메인 상태 오류는 명시적으로 표현한다. 예를 들어 결제되지 않은 주문을 구매 확정하거나, 구매 확정되지 않은 주문을 정산하려 하면 잘못된 상태 전이 오류로 실패해야 한다.

## 테스트 전략

테스트는 세 축으로 나눈다.

기능 테스트:

- 회원가입/로그인
- JWT 보호 API 접근
- 상품 등록과 조회
- 주문 생성과 취소
- 결제 승인과 취소
- 배송 시작과 완료
- 구매 확정
- 정산 생성과 중복 방지

도메인 테스트:

- 상품 상태 전이
- 주문 상태 전이
- 판매자/구매자 권한 규칙
- 정산 가능 조건

JPA 실험 테스트:

```text
jpalab
  PersistenceContextTest
  DirtyCheckingTest
  FlushTest
  LazyLoadingProxyTest
  NPlusOneTest
  FetchJoinTest
  DtoProjectionTest
  CascadeOrphanRemovalTest
  OptimisticLockTest
  BulkUpdateTest
  BatchSettlementTest
```

`jpalab` 패키지는 학습용이다. 단순히 기능 성공 여부만 확인하는 것이 아니라, JPA가 왜 그렇게 동작하는지 관찰하고 설명하는 테스트를 둔다.

핵심 JPA 실험 주제는 다음과 같다.

- 하나의 영속성 컨텍스트 안에서 같은 엔티티의 identity 보장
- 상품/주문 상태 변경에서 dirty checking 확인
- flush 시점과 SQL 전송 시점 확인
- lazy loading과 proxy 동작 확인
- 트랜잭션 밖 lazy loading 실패 확인
- 주문/상품/회원 목록 조회에서 N+1 재현
- fetch join과 DTO projection 비교
- 상품 이미지에서 cascade와 orphan removal 확인
- 같은 상품에 대한 동시 주문을 optimistic locking으로 방어
- bulk update 이후 영속성 컨텍스트 불일치 확인
- 대량 정산 처리에서 `flush()`와 `clear()` 필요성 확인

## 마일스톤

### Milestone 1: 기반 구축

- 공통 응답과 에러 구조
- Spring Security와 JWT
- 회원가입/로그인
- 테스트 fixture 구조
- API 통합 테스트 기본 구조

목표: 인증된 API 호출이 동작한다.

### Milestone 2: 상품 도메인

- 상품 등록/수정/삭제 또는 숨김
- 상품 이미지 추가/삭제
- 상품 목록/상세 조회
- 상품 이미지 cascade와 orphan removal 실험

목표: 판매자는 상품을 등록하고 구매자는 상품을 조회할 수 있다.

### Milestone 3: 주문 도메인

- 주문 생성
- 주문 취소
- 상품 상태를 `ON_SALE`에서 `RESERVED`로 전이
- 주문 취소 시 상품 상태 복구
- dirty checking 실험
- 같은 상품 동시 주문에 대한 optimistic locking 실험

목표: 거래 예약 흐름이 동작한다.

### Milestone 4: 결제와 배송

- fake `PaymentGateway`
- 결제 승인/취소
- fake `DeliveryClient`
- 배송 시작/완료
- 주문 상태 전이 테스트

목표: 외부 연동 경계가 존재하고 거래 흐름이 배송 완료까지 이어진다.

### Milestone 5: 구매 확정과 정산

- 구매 확정
- 상품 상태를 `SOLD_OUT`으로 전이
- 단건 정산 생성
- 중복 정산 방지
- 판매자 정산 조회

목표: 거래 완료와 판매자 정산이 동작한다.

### Milestone 6: 조회 최적화

- 상품 목록 조회 최적화
- 주문 목록 조회 최적화
- 정산 목록 조회 최적화
- N+1 재현
- fetch join 해결
- DTO projection 해결
- pagination 한계 실험

목표: 실무 JPA 조회 성능 문제를 재현하고 해결한다.

### Milestone 7: 배치 확장

- 구매 확정 주문 자동 정산
- 대량 정산 생성
- idempotency와 재시도 설계
- `flush()`와 `clear()` 실험
- bulk update 동작 실험
- 추후 Spring Batch 도입 검토

목표: 대규모 배치성 작업을 주요 학습 영역으로 확장한다.

## 설계 승인 기준

- 첫 백엔드 버전에서 전체 거래 흐름을 API로 완료할 수 있다.
- JWT 인증을 처음부터 사용한다.
- 결제와 배송은 fake adapter를 인터페이스 뒤에 둔다.
- 쓰기 모델과 조회 쿼리를 분리해 도메인 모델링과 조회 최적화를 모두 학습할 수 있다.
- `jpalab` 테스트를 학습 문서처럼 유지한다.
- 핵심 거래 모델을 크게 바꾸지 않고 배치 정산으로 확장할 수 있다.
