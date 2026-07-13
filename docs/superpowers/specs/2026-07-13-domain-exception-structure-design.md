# 도메인 예외 구조화 설계

## 목표

도메인 모델이 `IllegalArgumentException`, `IllegalStateException`, 그리고 영문 메시지 문자열로 업무 규칙 위반을 표현하는 방식을 코드 기반 계약으로 전환한다. 테스트는 문자열이 아닌 도메인 오류를 검증하고, 웹 클라이언트가 사용하는 API 오류 계약은 유지한다.

## 범위

전환 대상은 도메인 모델에서 직접 발생하는 업무 규칙 위반이다.

- `Product`, `Order`, `Payment`, `Delivery`, `Inventory`
- `RefundRequest`, `Settlement`, `Store`, `StoreMembership`

다음은 이번 전환 대상이 아니다.

- 설정 검증의 표준 예외: `JwtProperties`, `OrderAutoConfirmProperties`
- JWT 파싱·역할 변환 등 보안/기술 계층 예외
- 파일 저장소, JPA, Spring 등 프레임워크 또는 인프라 예외

## 호환성 계약

웹은 오류 응답의 `code`, `message`, `fieldErrors` 형식을 소비한다. 현재 웹은 `code`로 동작을 분기하지 않고, `fieldErrors` 또는 `message`를 화면에 표시한다.

이번 전환에서는 아래 외부 계약을 바꾸지 않는다.

- HTTP 상태 코드
- `ErrorResponse` JSON 필드와 구조
- 기존 `ErrorCode` 이름
- 기존 `ErrorCode`의 사용자용 한국어 메시지

따라서 웹 코드는 수정하지 않는다. 백엔드 API 테스트가 기존 상태와 `$.code`를 회귀 검증하고, 최종적으로 웹 빌드도 실행한다.

## 구조

공통 도메인 오류 추상화는 HTTP나 Spring에 의존하지 않는다.

```text
common/domain/error/
  DomainError.java
  DomainException.java

product/domain/ProductDomainError.java
order/domain/OrderDomainError.java
payment/domain/PaymentDomainError.java
delivery/domain/DeliveryDomainError.java
inventory/domain/InventoryDomainError.java
refund/domain/RefundRequestDomainError.java
settlement/domain/SettlementDomainError.java
store/domain/StoreDomainError.java
```

```java
public interface DomainError {
}

public final class DomainException extends RuntimeException {
    private final DomainError error;

    public DomainException(DomainError error) {
        super(error.toString());
        this.error = error;
    }

    public DomainError error() {
        return error;
    }
}
```

각 aggregate는 자신이 소유한 enum으로 규칙 위반을 표현한다. 예를 들어 `ProductDomainError`는 이미지 필수/제한/대표 이미지/정렬 순서와 상태 전이, 판매 정책·재고 설정 규칙을 표현한다.

각 테스트 가능한 업무 규칙에는 의미 있는 오류 코드 하나를 둔다. 같은 의미의 규칙은 재사용한다. 예를 들어 예약 상태가 필요하다는 규칙은 상품 복구와 판매 완료 처리에서 같은 오류 코드를 사용한다.

도메인 오류 enum에는 HTTP 상태, API 오류 코드, 사용자용 문구를 넣지 않는다. 동적 상태값은 더 이상 메시지 계약이 아니다. 이번 범위에서는 범용 오류 payload도 추가하지 않는다.

## 애플리케이션 매핑

도메인 오류는 `GlobalExceptionHandler`에서 직접 API 응답으로 변환하지 않는다. 동일한 도메인 규칙도 유스케이스별 외부 의미가 다를 수 있기 때문이다.

예를 들어 `ProductDomainError.NOT_ON_SALE`은 다음과 같이 달라진다.

- 주문 생성: `PRODUCT_NOT_ON_SALE`
- 장바구니 담기: `CART_PRODUCT_NOT_ON_SALE`
- 찜 추가: `WISHLIST_PRODUCT_NOT_ON_SALE`

따라서 각 애플리케이션 서비스가 자신이 소유한 유스케이스 관점에서 `DomainException`을 기존 `BusinessException(ErrorCode)`으로 명시적으로 매핑한다.

```java
try {
    Order order = Order.create(buyer, product);
    // ...
} catch (DomainException exception) {
    if (exception.error() == ProductDomainError.NOT_ON_SALE) {
        throw new BusinessException(ErrorCode.PRODUCT_NOT_ON_SALE, exception);
    }
    throw exception;
}
```

`BusinessException`에는 cause를 받는 생성자를 추가해 도메인 오류를 로그와 디버깅에서 보존한다. 응답은 기존처럼 `ErrorCode`만으로 생성한다.

기존의 `catch (IllegalArgumentException | IllegalStateException)`은 `catch (DomainException)`으로 치환한다. 매핑하지 않는 `DomainError`는 일반적인 `VALIDATION_ERROR`로 뭉뚱그리지 않고 다시 던진다. 이 규칙은 누락된 매핑을 테스트로 발견하게 한다.

## 테스트 전략

1. 각 도메인 단위 테스트의 예외 검증을 `DomainException`과 해당 도메인 오류 enum 검증으로 먼저 변경한다. 메시지 정확 비교는 제거한다.
2. 변경된 도메인 테스트가 실패하는지 확인한다.
3. 도메인 모델의 모든 직접 `IllegalArgumentException`/`IllegalStateException` 발생을 코드 기반 `DomainException`으로 전환한다.
4. 애플리케이션 서비스와 API 통합 테스트에서 기존 HTTP 상태와 `$.code`가 유지되는지 검증한다. 기존에 코드 검증이 없는 변환 경로는 추가한다.
5. JDK 21로 전체 백엔드 Gradle 테스트를 실행하고, 웹 빌드를 실행한다.

새 JUnit 테스트 메서드 이름은 프로젝트 규칙에 따라 한국어와 밑줄 표기법을 사용한다.

## 완료 기준

- 대상 도메인 모델에 직접 던지는 `IllegalArgumentException` 또는 `IllegalStateException`이 남아 있지 않다.
- 도메인 테스트가 메시지 문자열 대신 `DomainError`를 검증한다.
- 애플리케이션 계층은 `DomainException`만 업무 오류로 변환한다.
- 기존 API 오류 응답의 상태, `code`, `message`, `fieldErrors`가 유지된다.
- 전체 백엔드 테스트와 웹 빌드가 성공한다.
