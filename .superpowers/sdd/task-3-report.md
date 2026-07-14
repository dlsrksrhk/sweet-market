# Task 3 구현 보고서

## 구현 내용

- `PromotionPrice`와 `PromotionPricingService`를 추가했습니다. 활성 상태의 사업자 상점 프로모션만 후보로 조회하며, 선택 상품/상점 전체 범위, 정액·정률 할인, 원 단위 내림, 0원 하한, 최종가·우선순위·캠페인 ID 순 결정 규칙을 적용합니다.
- `quoteAll`은 중복과 `null` ID를 제거하고 최대 100개 상품을 한 번의 후보 조회와 상품 조회로 처리합니다.
- 주문에 정가, 프로모션 캠페인 ID, 할인액, 최종가 스냅샷을 저장했습니다. 기존 `productPrice` 응답 필드는 하위 호환성을 위해 최종가를 반환하며, 명시적인 스냅샷 필드를 추가했습니다.
- 직접 주문과 장바구니 체크아웃이 상품 구매 가능 검증 뒤 주문 직전에 견적을 계산하도록 변경했습니다.
- 결제 승인, 정산, 주문 응답 및 판매자 금액 집계가 현재 상품 가격이 아닌 주문 최종가를 사용하도록 변경했습니다.

## TDD 및 검증

- `PromotionPricingServiceTest`를 먼저 작성하고 서비스 클래스 부재로 컴파일 실패하는 RED 상태를 확인했습니다.
- 주문·장바구니 스냅샷 API 테스트를 먼저 작성하고 스냅샷 응답 필드 부재로 실패하는 RED 상태를 확인했습니다.
- 다음 명령을 JDK 21 및 JWT 시크릿으로 실행했습니다.

```powershell
cd backend
$env:JAVA_HOME='C:\Users\kdh\.jdks\corretto-21.0.7'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat test --tests com.sweet.market.promotion.application.PromotionPricingServiceTest --tests com.sweet.market.order.OrderApiTest --tests com.sweet.market.cart.CartCheckoutApiTest --tests com.sweet.market.payment.PaymentApiTest
```

- 결과: `BUILD SUCCESSFUL` (36개 테스트, 실패 0건)
