# JPA Lab Learning Guide

이 문서는 `backend/src/test/java/com/sweet/market/jpalab` 패키지의 테스트를 실행하면서 JPA를 깊게 이해하기 위한 학습 가이드다.

`jpalab` 패키지는 일반적인 기능 검증보다 JPA 동작 원리를 관찰하는 데 초점을 둔다. 각 테스트는 영속성 컨텍스트, flush, dirty checking, cascade, orphanRemoval, optimistic locking, lazy loading, N+1, EntityGraph 같은 개념을 실제 코드와 SQL 실행 흐름으로 확인하도록 설계되어 있다.

## 1. 실행 준비

### 기본 실행 명령

이 PC에서는 문서에 적힌 `C:\java\jdk-21` 경로가 없을 수 있다. 그런 경우 `JAVA_HOME`을 비우면 Gradle toolchain 설정을 통해 테스트가 실행된다.

```powershell
cd backend
Remove-Item Env:JAVA_HOME -ErrorAction SilentlyContinue
$env:JWT_SECRET='sweet-market-local-test-secret-key-32bytes-minimum'
.\gradlew.bat --no-daemon test --tests com.sweet.market.jpalab.*
```

### 전체 jpalab 테스트

```powershell
.\gradlew.bat --no-daemon test --tests com.sweet.market.jpalab.*
```

### 개별 테스트 클래스 실행

```powershell
.\gradlew.bat --no-daemon test --tests com.sweet.market.jpalab.DirtyCheckingTest
.\gradlew.bat --no-daemon test --tests com.sweet.market.jpalab.CascadeOrphanRemovalTest
.\gradlew.bat --no-daemon test --tests com.sweet.market.jpalab.OptimisticLockTest
.\gradlew.bat --no-daemon test --tests com.sweet.market.jpalab.ProductQueryOptimizationTest
.\gradlew.bat --no-daemon test --tests com.sweet.market.jpalab.OrderQueryOptimizationTest
.\gradlew.bat --no-daemon test --tests com.sweet.market.jpalab.SettlementQueryOptimizationTest
```

## 2. 추천 학습 순서

1. `DirtyCheckingTest`
2. `CascadeOrphanRemovalTest`
3. `OptimisticLockTest`
4. `ProductQueryOptimizationTest`
5. `OrderQueryOptimizationTest`
6. `SettlementQueryOptimizationTest`

처음 세 테스트는 JPA가 엔티티를 어떻게 관리하는지 보여준다. 뒤의 세 테스트는 연관관계 조회가 어떻게 N+1 문제를 만들고, 이를 어떻게 해결하는지 보여준다.

## 3. QueryOptimizationTestSupport

파일:

```text
backend/src/test/java/com/sweet/market/jpalab/QueryOptimizationTestSupport.java
```

### 학습 목표

- Hibernate `Statistics`로 실제 SQL statement 수를 측정하는 방법을 익힌다.
- 1차 캐시가 테스트 결과를 가리지 않도록 `flush()`와 `clear()`를 사용하는 이유를 이해한다.

### 핵심 메서드

```java
protected void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
}
```

`flush()`는 영속성 컨텍스트의 변경을 DB에 반영한다. `clear()`는 1차 캐시를 비운다. 이 둘을 함께 써야 이후 조회가 메모리 캐시가 아니라 DB 조회와 lazy loading을 통해 일어난다.

```java
protected void resetStatistics() {
    Statistics statistics = statistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();
}
```

Hibernate 통계를 켜고 기존 카운터를 초기화한다.

```java
protected long queryCount() {
    return statistics().getPrepareStatementCount();
}
```

테스트 구간에서 준비된 SQL statement 수를 읽는다. N+1 테스트에서 핵심 지표로 사용된다.

### 관찰 포인트

- 쿼리 수를 세기 전에는 반드시 `resetStatistics()`를 호출한다.
- 테스트 데이터 저장 시 발생한 insert 쿼리가 측정에 섞이지 않도록 `flushAndClear()` 후 통계를 초기화한다.
- 조회 최적화 테스트는 결과 개수뿐 아니라 query count까지 assert한다.

## 4. DirtyCheckingTest

파일:

```text
backend/src/test/java/com/sweet/market/jpalab/DirtyCheckingTest.java
```

테스트:

```java
dirty_checking은_주문_취소와_상품_상태_복구를_update로_반영한다
```

### 학습 목표

- 영속 상태 엔티티의 필드 변경이 별도 `save()` 없이 DB update로 반영되는 과정을 이해한다.
- 도메인 메서드가 여러 엔티티 상태를 바꾸는 경우 dirty checking이 어떻게 동작하는지 확인한다.

### 테스트 흐름

1. 판매자와 구매자를 저장한다.
2. 상품을 생성한다.
3. 주문을 생성한다.
   - 이때 상품은 `ON_SALE`에서 `RESERVED`로 바뀐다.
4. `flush()`와 `clear()`로 DB 반영 후 1차 캐시를 비운다.
5. 주문을 다시 조회한다.
6. `foundOrder.cancel()`을 호출한다.
7. `flush()`와 `clear()`를 다시 호출한다.
8. 주문과 상품을 DB에서 다시 조회한다.
9. 주문은 `CANCELED`, 상품은 `ON_SALE`인지 검증한다.

### 핵심 코드

```java
Order foundOrder = orderRepository.findWithBuyerAndProductById(order.getId()).orElseThrow();

foundOrder.cancel();
entityManager.flush();
entityManager.clear();
```

여기서 `orderRepository.save(foundOrder)`를 호출하지 않는다. 그래도 JPA는 트랜잭션 안에서 영속 엔티티의 변경을 감지해 update SQL을 만든다.

### 깊게 볼 포인트

- `foundOrder.cancel()` 내부에서 주문 상태와 상품 상태가 함께 바뀐다.
- JPA는 영속성 컨텍스트에 로딩된 엔티티의 최초 snapshot과 현재 상태를 비교한다.
- 변경이 있으면 flush 시점에 update SQL을 만든다.
- `clear()` 후 다시 조회하는 이유는 1차 캐시의 객체 상태가 아니라 DB 반영 결과를 확인하기 위해서다.

### 실무 포인트

- JPA에서는 “수정 후 save”가 필수가 아니다.
- 중요한 것은 엔티티가 영속 상태인지, 트랜잭션 안인지, flush가 언제 일어나는지다.
- 도메인 메서드에 상태 전이 규칙을 모으면 service 레이어가 엔티티 내부 값을 직접 조작하지 않아도 된다.

## 5. CascadeOrphanRemovalTest

파일:

```text
backend/src/test/java/com/sweet/market/jpalab/CascadeOrphanRemovalTest.java
```

## 5.1 cascade persist

테스트:

```java
cascade_persist는_상품과_함께_상품_이미지를_저장한다
```

### 학습 목표

- 부모 엔티티 저장이 자식 엔티티 저장으로 전파되는 cascade persist를 이해한다.
- aggregate root인 `Product`가 `ProductImage` 생명주기를 소유하는 구조를 확인한다.

### 테스트 흐름

1. 판매자를 저장한다.
2. `Product.create(...)`로 상품을 만든다.
3. `product.addImage(...)`를 두 번 호출한다.
4. `productRepository.save(product)`만 호출한다.
5. `flush()`와 `clear()` 후 상품을 이미지와 함께 다시 조회한다.
6. 상품 이미지가 2개 저장되었는지 확인한다.

### 핵심 코드

```java
Product product = Product.create(seller, "MacBook Pro", "M3 laptop", 2_000_000L);
product.addImage("https://example.com/macbook-1.jpg");
product.addImage("https://example.com/macbook-2.jpg");

productRepository.save(product);
```

이미지 repository를 직접 호출하지 않는다. 부모인 `Product`를 저장하면 cascade 설정에 의해 자식 이미지도 저장된다.

### 실무 포인트

- cascade는 부모가 자식의 생명주기를 소유할 때 적합하다.
- 상품 이미지처럼 상품 없이 독립적으로 존재하지 않는 데이터는 cascade 대상이 될 수 있다.
- 반대로 회원, 주문, 결제처럼 독립 생명주기를 가진 aggregate 사이에는 cascade를 신중히 써야 한다.

## 5.2 orphanRemoval

테스트:

```java
orphanRemoval은_상품_컬렉션에서_제거된_이미지를_삭제한다
```

### 학습 목표

- 부모 컬렉션에서 제거된 자식 엔티티가 DB에서도 삭제되는 orphanRemoval을 이해한다.

### 테스트 흐름

1. 이미지 2개를 가진 상품을 저장한다.
2. 상품을 이미지와 함께 다시 조회한다.
3. 첫 번째 이미지 ID를 기억한다.
4. `foundProduct.removeImage(imageId)`를 호출한다.
5. flush 후 이미지 row가 삭제되었는지 확인한다.

### 핵심 코드

```java
foundProduct.removeImage(imageId);
entityManager.flush();
entityManager.clear();

assertThat(productImageRepository.existsById(imageId)).isFalse();
```

컬렉션에서 제거했을 뿐인데 DB row가 삭제된다. 이것이 `orphanRemoval = true`의 효과다.

### 실무 포인트

- orphanRemoval은 “부모와의 관계가 끊기면 자식은 존재 의미가 없다”는 모델에 적합하다.
- 단순 관계 해제와 삭제는 다르다. orphanRemoval은 삭제까지 수행한다.
- 실수로 컬렉션을 통째로 교체하면 의도치 않은 delete가 발생할 수 있으므로 조심해야 한다.

## 6. OptimisticLockTest

파일:

```text
backend/src/test/java/com/sweet/market/jpalab/OptimisticLockTest.java
```

테스트:

```java
같은_상품을_두_트랜잭션이_예약하면_나중_커밋이_optimistic_lock으로_실패한다
```

### 학습 목표

- `@Version` 기반 optimistic locking이 동시 수정 충돌을 어떻게 감지하는지 이해한다.
- 서로 다른 두 영속성 컨텍스트가 같은 row를 수정할 때 어떤 일이 생기는지 확인한다.

### 테스트 흐름

1. 상품 하나를 저장한다.
2. `EntityManager` 두 개를 직접 만든다.
3. 트랜잭션 두 개를 각각 시작한다.
4. 두 트랜잭션에서 같은 상품을 조회한다.
5. 두 상품 객체에서 모두 `reserve()`를 호출한다.
6. 첫 번째 트랜잭션을 commit한다.
7. 두 번째 트랜잭션 commit이 optimistic lock 예외로 실패하는지 검증한다.

### 핵심 코드

```java
Product firstProduct = firstEntityManager.find(Product.class, productId);
Product secondProduct = secondEntityManager.find(Product.class, productId);

firstProduct.reserve();
secondProduct.reserve();

firstTransaction.commit();

assertThatThrownBy(secondTransaction::commit)
        .isInstanceOf(RollbackException.class)
        .hasCauseInstanceOf(OptimisticLockException.class);
```

### 깊게 볼 포인트

- 두 트랜잭션은 같은 version 값을 가진 상품을 읽는다.
- 첫 번째 commit이 성공하면서 DB version이 증가한다.
- 두 번째 commit은 이전 version 기준으로 update하려고 하므로 영향 row가 없거나 version mismatch가 발생한다.
- JPA는 이를 optimistic lock 실패로 해석한다.

### 실무 포인트

- optimistic locking은 먼저 lock을 잡지 않는다.
- 충돌 가능성은 낮지만 충돌이 생기면 실패시켜도 되는 경우에 적합하다.
- 같은 상품을 동시에 주문하는 상황처럼 “마지막 커밋자가 이기면 안 되는” 비즈니스에 유용하다.
- 실패 후 사용자에게 재시도 안내나 품절/예약 실패 메시지를 보여줘야 한다.

## 7. ProductQueryOptimizationTest

파일:

```text
backend/src/test/java/com/sweet/market/jpalab/ProductQueryOptimizationTest.java
```

## 7.1 상품 목록 N+1 재현

테스트:

```java
상품_목록_단순_조회는_seller_N_plus_1이_발생한다
```

### 학습 목표

- lazy association 접근이 추가 select를 발생시키는 구조를 이해한다.
- N+1 문제를 쿼리 수로 직접 관찰한다.

### 테스트 흐름

1. 서로 다른 seller를 가진 상품 3개를 저장한다.
2. `flushAndClear()`로 1차 캐시를 비운다.
3. Hibernate statistics를 초기화한다.
4. JPQL로 상품만 조회한다.
5. 각 상품의 `product.getSeller().getNickname()`에 접근한다.
6. query count가 4 이상인지 확인한다.

### 핵심 코드

```java
try (Stream<Product> products = entityManager.createQuery(
                "select p from Product p where p.status = :status order by p.id desc",
                Product.class
        )
        .setParameter("status", ProductStatus.ON_SALE)
        .getResultStream()) {
    sellerNicknames = products
            .map(product -> product.getSeller().getNickname())
            .toList();
}

assertThat(queryCount()).isGreaterThanOrEqualTo(4);
```

### 깊게 볼 포인트

- 최초 상품 목록 조회 1번이 발생한다.
- seller는 lazy association이므로 접근 시점에 추가 조회된다.
- seller가 3명이라면 seller 조회가 추가로 발생한다.
- 그래서 N개의 상품에 대해 1 + N 형태가 된다.

### 왜 getResultStream을 쓰는가

테스트 주석에 중요한 힌트가 있다.

```java
// Streaming keeps lazy seller loads observable; getResultList can be masked by default_batch_fetch_size.
```

프로젝트에는 `default_batch_fetch_size`가 설정되어 있다. 이 설정 때문에 `getResultList()`에서는 lazy loading이 batch fetch로 묶여 N+1이 덜 선명하게 보일 수 있다. 이 테스트는 학습용으로 N+1을 확실히 관찰하기 위해 stream을 사용한다.

## 7.2 상품 목록 최적화

테스트:

```java
상품_목록_최적화_조회는_seller를_함께_로딩한다
```

### 학습 목표

- 목록 화면에서 필요한 연관만 미리 로딩하는 최적화 전략을 이해한다.

### 테스트 흐름

1. 상품 3개를 저장한다.
2. 최적화된 repository 메서드로 상품 목록을 조회한다.
3. seller nickname을 읽는다.
4. query count가 2 이하인지 확인한다.

### 핵심 코드

```java
List<String> sellerNicknames = productRepository.findByStatusOrderByIdDesc(
                ProductStatus.ON_SALE,
                PageRequest.of(0, 10)
        )
        .getContent()
        .stream()
        .map(product -> product.getSeller().getNickname())
        .toList();

assertThat(queryCount()).isLessThanOrEqualTo(2);
```

`Page` 조회는 content query와 count query가 나갈 수 있으므로 2 이하를 기대한다.

## 7.3 필요한 연관만 로딩

테스트:

```java
상품_목록_최적화_조회는_images를_로딩하지_않는다
```

### 학습 목표

- 최적화는 모든 연관을 eager loading하는 것이 아니라 화면에 필요한 연관만 로딩하는 것임을 이해한다.

### 핵심 코드

```java
assertThat(products)
        .allSatisfy(product -> {
            assertThat(persistenceUnitUtil.isLoaded(product, "seller")).isTrue();
            assertThat(persistenceUnitUtil.isLoaded(product, "images")).isFalse();
        });
```

### 실무 포인트

- 목록 화면에는 seller nickname이 필요하지만 images 전체 컬렉션은 필요하지 않을 수 있다.
- 불필요한 컬렉션을 로딩하면 쿼리와 메모리 비용이 늘어난다.
- `PersistenceUnitUtil.isLoaded()`는 테스트에서 연관 로딩 여부를 확인하는 데 유용하다.

## 8. OrderQueryOptimizationTest

파일:

```text
backend/src/test/java/com/sweet/market/jpalab/OrderQueryOptimizationTest.java
```

## 8.1 주문 목록 N+1 재현

테스트:

```java
주문_목록_단순_조회는_product_seller_N_plus_1이_발생한다
```

### 학습 목표

- 주문 목록에서 `Order -> Product -> Seller` 연쇄 lazy loading이 어떻게 쿼리 폭증을 만드는지 이해한다.

### 테스트 흐름

1. 한 구매자에 대해 서로 다른 seller의 상품 주문 3개를 만든다.
2. 주문만 JPQL로 조회한다.
3. 각 주문의 상품 제목과 판매자 닉네임에 접근한다.
4. query count가 7 이상인지 확인한다.

### 핵심 코드

```java
summaries = orders
        .map(order -> {
            Product product = order.getProduct();
            return product.getTitle() + ":" + product.getSeller().getNickname();
        })
        .toList();

assertThat(queryCount()).isGreaterThanOrEqualTo(7);
```

### 깊게 볼 포인트

- 주문 목록 조회 1번
- 각 주문의 product lazy loading
- 각 product의 seller lazy loading
- 그래서 product와 seller가 함께 필요한 화면에서는 사전 로딩이 중요하다.

## 8.2 주문 목록 최적화 조회

테스트:

```java
주문_목록_최적화_조회는_product_seller를_함께_로딩한다
```

### 학습 목표

- query service가 응답을 만들 때 필요한 연관을 미리 로딩하도록 repository를 설계하는 방법을 이해한다.

### 핵심 코드

```java
List<OrderSummaryResponse> summaries = orderQueryService.findMine(buyer.getId(), PageRequest.of(0, 10))
        .getContent();

assertThat(queryCount()).isLessThanOrEqualTo(2);
```

### 실무 포인트

- API 응답 DTO가 어떤 필드를 필요로 하는지 먼저 확인해야 한다.
- 그 필드에 필요한 연관을 repository 쿼리에서 명시적으로 로딩한다.
- Page 조회에서는 count query가 추가될 수 있다.

## 8.3 로딩 상태 검증

테스트:

```java
주문_목록_최적화_조회는_product_seller가_로딩되어_있다
```

### 핵심 코드

```java
assertThat(orders)
        .allSatisfy(order -> {
            assertThat(persistenceUnitUtil.isLoaded(order, "product")).isTrue();
            assertThat(persistenceUnitUtil.isLoaded(order.getProduct(), "seller")).isTrue();
        });
```

쿼리 수만 줄었다고 끝이 아니다. 실제로 필요한 association이 loaded 상태인지 함께 검증한다.

## 9. SettlementQueryOptimizationTest

파일:

```text
backend/src/test/java/com/sweet/market/jpalab/SettlementQueryOptimizationTest.java
```

## 9.1 정산 목록 N+1 재현

테스트:

```java
정산_목록_단순_조회는_주문_상품_N_plus_1이_발생한다
```

### 학습 목표

- 정산 목록에서 `Settlement -> Order -> Product` 연관 접근이 어떤 추가 쿼리를 만드는지 이해한다.

### 테스트 흐름

1. 판매자 한 명에 대해 정산 3개를 만든다.
2. 정산만 JPQL로 조회한다.
3. 각 정산의 주문과 상품 제목에 접근한다.
4. query count가 7 이상인지 검증한다.

### 핵심 코드

```java
productTitles = settlements
        .map(settlement -> settlement.getOrder().getId() + ":" + settlement.getOrder().getProduct().getTitle())
        .toList();

assertThat(queryCount()).isGreaterThanOrEqualTo(7);
```

### 실무 포인트

- 정산 화면은 판매자에게 반복적으로 노출되는 목록 화면이다.
- 목록 화면에서 N+1이 발생하면 데이터가 조금만 늘어도 응답 시간이 커진다.

## 9.2 정산 목록 최적화

테스트:

```java
정산_목록_최적화_조회는_한_번의_쿼리로_응답한다
```

### 학습 목표

- 정산 응답에 필요한 order/product/seller graph를 한 번에 로딩하는 방식을 이해한다.

### 핵심 코드

```java
List<SettlementResponse> responses = settlementQueryService.findMine(seller.getId());

assertThat(responses).hasSize(3);
assertThat(queryCount()).isLessThanOrEqualTo(1);
```

정산 목록은 Page가 아니라 List 조회이므로 count query가 없다. 따라서 한 번의 조회로 응답을 만들 수 있다.

## 9.3 EntityGraph 로딩 상태 검증

테스트:

```java
정산_목록_최적화_조회는_주문과_상품을_함께_로딩한다
```

### 핵심 코드

```java
assertThat(settlements)
        .allSatisfy(settlement -> {
            Order order = settlement.getOrder();

            assertThat(persistenceUnitUtil.isLoaded(settlement, "order")).isTrue();
            assertThat(persistenceUnitUtil.isLoaded(order, "product")).isTrue();
            assertThat(persistenceUnitUtil.isLoaded(settlement, "seller")).isTrue();
        });
```

EntityGraph가 의도대로 association을 로딩했는지 확인한다.

## 9.4 필요한 그래프만 선언하기

테스트:

```java
정산_목록_최적화_조회는_필요한_그래프만_선언한다
```

### 학습 목표

- repository 메서드의 `@EntityGraph` 선언 자체를 테스트로 고정한다.
- 과도한 연관 로딩이 들어오는 것을 방지한다.

### 핵심 코드

```java
Method method = SettlementRepository.class.getMethod("findBySellerIdOrderByIdDesc", Long.class);

EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);

assertThat(entityGraph.attributePaths())
        .containsExactlyInAnyOrder("order", "order.product", "seller");
```

### 실무 포인트

- 성능 최적화는 기능 요구사항처럼 테스트로 보호할 수 있다.
- 필요한 graph를 명시적으로 검증하면 누군가 불필요한 association을 추가하는 회귀를 막을 수 있다.

## 10. 각 테스트를 실행하며 관찰할 것

### SQL 로그

`application.yaml`에는 SQL 로그 설정이 있다.

```yaml
logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace
```

테스트 실행 중 콘솔에서 SQL과 bind 값을 보며 아래를 확인한다.

- dirty checking 테스트에서 update SQL이 언제 나가는가
- cascade 테스트에서 product image insert가 어떻게 나가는가
- orphanRemoval 테스트에서 delete SQL이 발생하는가
- N+1 테스트에서 select가 몇 번 반복되는가
- 최적화 테스트에서 join 또는 entity graph 기반 조회가 쿼리 수를 줄이는가

### flush와 clear

`flush()`는 SQL 반영, `clear()`는 1차 캐시 제거다.

JPA 학습 테스트에서 자주 등장하는 이유는 명확하다. DB에 실제로 반영된 결과와 이후 lazy loading을 관찰하기 위해서다.

### query count

조회 최적화 테스트는 `queryCount()`를 통해 성능 특성을 숫자로 고정한다.

```java
assertThat(queryCount()).isGreaterThanOrEqualTo(7);
assertThat(queryCount()).isLessThanOrEqualTo(2);
```

앞의 assert는 N+1이 발생한다는 것을 보여주는 학습용 검증이다. 뒤의 assert는 최적화가 유지되는지 지키는 회귀 방지 검증이다.

## 11. 직접 실험해볼 과제

아래 실험은 테스트를 더 깊게 이해하는 데 도움이 된다. 실험 후에는 원래 코드로 되돌린다.

### 실험 1: DirtyCheckingTest에서 flush 제거하기

`foundOrder.cancel()` 후 `entityManager.flush()`를 제거하면 언제 DB update가 발생하는지 관찰한다.

학습 포인트:

- 트랜잭션 commit 직전에도 flush가 일어난다.
- 하지만 테스트 중간에 DB 재조회로 검증하려면 명시적 flush가 필요하다.

### 실험 2: Cascade 설정 제거 상상하기

`Product`의 images 매핑에서 cascade가 없다면 `productRepository.save(product)`만으로 이미지가 저장될 수 있는지 생각해본다.

학습 포인트:

- 부모 저장과 자식 저장은 자동이 아니다.
- cascade는 명시적 생명주기 전파 설정이다.

### 실험 3: orphanRemoval 없이 컬렉션에서 제거하기

orphanRemoval이 없다면 컬렉션에서 제거된 이미지 row가 어떻게 될지 예상해본다.

학습 포인트:

- 관계만 끊길 수도 있고, row가 남을 수도 있다.
- DB nullable FK 여부와 매핑에 따라 결과가 달라진다.

### 실험 4: OptimisticLockTest에서 두 번째 조회를 첫 번째 commit 이후로 옮기기

두 번째 트랜잭션이 첫 번째 commit 이후 상품을 조회하면 optimistic lock 실패가 발생할지 생각해본다.

학습 포인트:

- 최신 version을 읽으면 충돌이 아니다.
- optimistic lock은 같은 과거 version을 기반으로 동시에 수정할 때 의미가 있다.

### 실험 5: ProductQueryOptimizationTest에서 seller 접근 제거하기

단순 조회 테스트에서 `product.getSeller().getNickname()` 접근을 제거하면 query count가 어떻게 변할지 본다.

학습 포인트:

- lazy association은 접근하기 전까지 조회하지 않는다.
- N+1은 “조회 자체”가 아니라 “조회 후 연관 접근”에서 터진다.

### 실험 6: EntityGraph에 images 추가하기

상품 목록 최적화에 images까지 로딩하도록 바꾸면 `상품_목록_최적화_조회는_images를_로딩하지_않는다` 테스트가 실패한다.

학습 포인트:

- 최적화는 필요한 데이터를 정확히 가져오는 것이다.
- 불필요한 eager loading도 성능 회귀다.

## 12. 핵심 개념 요약

### 영속성 컨텍스트

JPA가 엔티티 객체를 관리하는 1차 캐시이자 변경 추적 공간이다. 같은 트랜잭션 안에서 조회한 엔티티는 영속성 컨텍스트에 보관된다.

### Dirty Checking

영속 엔티티의 최초 상태와 현재 상태를 비교해서 변경된 필드를 update SQL로 반영하는 기능이다.

### flush

영속성 컨텍스트의 변경 내용을 DB에 SQL로 반영하는 작업이다. commit 전에 자동으로 일어나지만, 테스트에서는 관찰을 위해 명시적으로 호출한다.

### clear

영속성 컨텍스트를 비우는 작업이다. clear 후 다시 조회하면 DB에서 새로 읽어온다.

### Cascade

부모 엔티티의 저장/삭제 같은 생명주기 이벤트를 자식 엔티티에 전파하는 설정이다.

### orphanRemoval

부모와의 관계가 끊긴 자식 엔티티를 고아 객체로 보고 삭제하는 설정이다.

### Optimistic Lock

데이터를 먼저 잠그지 않고 version으로 충돌을 감지하는 동시성 제어 방식이다.

### Lazy Loading

연관 엔티티를 처음부터 가져오지 않고 실제 접근 시점에 조회하는 방식이다.

### N+1

목록 1번 조회 후 각 row의 lazy association 접근 때문에 추가 쿼리 N번이 발생하는 문제다.

### EntityGraph

특정 repository 조회에서 함께 로딩할 association을 명시하는 JPA 기능이다.

### Hibernate Statistics

Hibernate가 실행한 statement 수 등 내부 통계를 제공하는 기능이다. 테스트에서 성능 회귀를 감지하는 데 사용할 수 있다.

