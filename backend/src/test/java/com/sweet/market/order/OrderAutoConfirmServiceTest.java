package com.sweet.market.order;

import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.domain.DeliveryStatus;
import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.application.OrderAutoConfirmProperties;
import com.sweet.market.order.application.OrderAutoConfirmResult;
import com.sweet.market.order.application.OrderAutoConfirmService;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.domain.OrderStatus;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = "market.order.auto-confirm.limit=1")
@Transactional
class OrderAutoConfirmServiceTest extends IntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private OrderAutoConfirmService orderAutoConfirmService;

    @Test
    void 배송완료_7일이_지난_주문은_자동_구매확정된다() {
        LocalDateTime executedAt = LocalDateTime.of(2026, 6, 15, 10, 0);
        Order order = saveDeliveredOrder("eligible", executedAt.minusDays(7).minusSeconds(1));

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(executedAt);

        Order foundOrder = findOrder(order);
        assertThat(result.confirmedCount()).isEqualTo(1);
        assertThat(result.deliveredBefore()).isEqualTo(executedAt.minusDays(7));
        assertThat(result.thresholdDays()).isEqualTo(7);
        assertThat(result.executedAt()).isEqualTo(executedAt);
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(foundOrder.getConfirmedAt()).isNotNull();
        assertThat(foundOrder.getProduct().getStatus()).isEqualTo(ProductStatus.SOLD_OUT);
    }

    @Test
    void 배송완료_7일이_지나지_않은_주문은_자동_구매확정하지_않는다() {
        LocalDateTime executedAt = LocalDateTime.of(2026, 6, 15, 10, 0);
        Order order = saveDeliveredOrder("recent", executedAt.minusDays(7));

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(executedAt);

        Order foundOrder = findOrder(order);
        assertThat(result.confirmedCount()).isZero();
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(foundOrder.getConfirmedAt()).isNull();
        assertThat(foundOrder.getProduct().getStatus()).isEqualTo(ProductStatus.RESERVED);
    }

    @Test
    void 이미_구매확정된_주문은_자동_구매확정에서_제외한다() {
        LocalDateTime executedAt = LocalDateTime.of(2026, 6, 15, 10, 0);
        Order order = saveDeliveredOrder("confirmed", executedAt.minusDays(8));
        orderAutoConfirmService.confirmDeliveredOrders(executedAt);

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(executedAt.plusMinutes(1));

        Order foundOrder = findOrder(order);
        assertThat(result.confirmedCount()).isZero();
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void 배송중_주문은_자동_구매확정에서_제외한다() {
        LocalDateTime executedAt = LocalDateTime.of(2026, 6, 15, 10, 0);
        Order order = saveShippingOrder("shipping", executedAt.minusDays(8));

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(executedAt);

        Order foundOrder = findOrder(order);
        assertThat(result.confirmedCount()).isZero();
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.SHIPPING);
        assertThat(foundOrder.getConfirmedAt()).isNull();
        assertThat(foundOrder.getProduct().getStatus()).isEqualTo(ProductStatus.RESERVED);
    }

    @Test
    void 결제완료_주문은_자동_구매확정에서_제외한다() {
        LocalDateTime executedAt = LocalDateTime.of(2026, 6, 15, 10, 0);
        Order order = savePaidOrder("paid");

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(executedAt);

        Order foundOrder = findOrder(order);
        assertThat(result.confirmedCount()).isZero();
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(foundOrder.getConfirmedAt()).isNull();
        assertThat(foundOrder.getProduct().getStatus()).isEqualTo(ProductStatus.RESERVED);
    }

    @Test
    void 취소된_주문은_자동_구매확정에서_제외한다() {
        LocalDateTime executedAt = LocalDateTime.of(2026, 6, 15, 10, 0);
        Order order = saveCanceledOrder("canceled");

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(executedAt);

        Order foundOrder = findOrder(order);
        assertThat(result.confirmedCount()).isZero();
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CANCELED);
    }

    @Test
    void 자동_구매확정은_반복_실행해도_중복_처리하지_않는다() {
        LocalDateTime executedAt = LocalDateTime.of(2026, 6, 15, 10, 0);
        Order order = saveDeliveredOrder("repeat", executedAt.minusDays(8));

        OrderAutoConfirmResult firstResult = orderAutoConfirmService.confirmDeliveredOrders(executedAt);
        OrderAutoConfirmResult secondResult = orderAutoConfirmService.confirmDeliveredOrders(executedAt.plusMinutes(1));

        Order foundOrder = findOrder(order);
        assertThat(firstResult.confirmedCount()).isEqualTo(1);
        assertThat(secondResult.confirmedCount()).isZero();
        assertThat(foundOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void 설정된_처리_한도까지만_자동_구매확정한다() {
        LocalDateTime executedAt = LocalDateTime.of(2026, 6, 15, 10, 0);
        Order firstOrder = saveDeliveredOrder("limit-first", executedAt.minusDays(8));
        Order secondOrder = saveDeliveredOrder("limit-second", executedAt.minusDays(8));

        OrderAutoConfirmResult result = orderAutoConfirmService.confirmDeliveredOrders(executedAt);

        Order foundFirstOrder = findOrder(firstOrder);
        Order foundSecondOrder = findOrder(secondOrder);
        assertThat(result.confirmedCount()).isEqualTo(1);
        assertThat(foundFirstOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(foundSecondOrder.getStatus()).isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    void 자동_구매확정은_동시_실행을_단일_인스턴스에서_직렬화한다() throws Exception {
        DeliveryRepository repository = mock(DeliveryRepository.class);
        OrderAutoConfirmService service = new OrderAutoConfirmService(
                repository,
                new OrderAutoConfirmProperties(7, 100)
        );
        Delivery delivery = createDeliveredDelivery("concurrent");
        LocalDateTime executedAt = LocalDateTime.of(2026, 6, 15, 10, 0);
        CountDownLatch firstQueryEntered = new CountDownLatch(1);
        CountDownLatch secondQueryEntered = new CountDownLatch(1);
        CountDownLatch releaseFirstQuery = new CountDownLatch(1);
        AtomicInteger queryCount = new AtomicInteger();

        when(repository.findAutoConfirmCandidates(
                any(LocalDateTime.class),
                eq(DeliveryStatus.DELIVERED),
                eq(OrderStatus.DELIVERED),
                any(Pageable.class)
        )).thenAnswer(invocation -> {
            int currentQuery = queryCount.incrementAndGet();
            if (currentQuery == 1) {
                firstQueryEntered.countDown();
                assertThat(releaseFirstQuery.await(2, TimeUnit.SECONDS)).isTrue();
            } else if (currentQuery == 2) {
                secondQueryEntered.countDown();
            }

            return delivery.getOrder().getStatus() == OrderStatus.DELIVERED
                    ? List.of(delivery)
                    : List.of();
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OrderAutoConfirmResult> firstRun = executor.submit(() -> service.confirmDeliveredOrders(executedAt));
            assertThat(firstQueryEntered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<OrderAutoConfirmResult> secondRun = executor.submit(
                    () -> service.confirmDeliveredOrders(executedAt.plusSeconds(1))
            );

            assertThat(secondQueryEntered.await(200, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirstQuery.countDown();

            assertThat(firstRun.get(2, TimeUnit.SECONDS).confirmedCount()).isEqualTo(1);
            assertThat(secondRun.get(2, TimeUnit.SECONDS).confirmedCount()).isZero();
        } finally {
            executor.shutdownNow();
        }
    }

    private Order savePaidOrder(String key) {
        Member seller = memberRepository.save(Member.create("seller-" + key + "@example.com", "encoded-password", "seller-" + key));
        Member buyer = memberRepository.save(Member.create("buyer-" + key + "@example.com", "encoded-password", "buyer-" + key));
        Product product = productRepository.save(Product.create(seller, "MacBook Pro " + key, "M3 laptop", 2_000_000L));
        Order order = orderRepository.save(Order.create(buyer, product));
        order.markPaid();
        entityManager.flush();
        entityManager.clear();
        return order;
    }

    private Order saveCanceledOrder(String key) {
        Member seller = memberRepository.save(Member.create("seller-" + key + "@example.com", "encoded-password", "seller-" + key));
        Member buyer = memberRepository.save(Member.create("buyer-" + key + "@example.com", "encoded-password", "buyer-" + key));
        Product product = productRepository.save(Product.create(seller, "MacBook Pro " + key, "M3 laptop", 2_000_000L));
        Order order = orderRepository.save(Order.create(buyer, product));
        order.cancel();
        entityManager.flush();
        entityManager.clear();
        return order;
    }

    private Order saveShippingOrder(String key, LocalDateTime completedAt) {
        Order order = savePaidOrder(key);
        Order foundOrder = orderRepository.findWithBuyerAndProductById(order.getId()).orElseThrow();
        Delivery delivery = deliveryRepository.save(Delivery.start(foundOrder, "tracking-" + key));
        entityManager.flush();
        jdbcTemplate.update(
                "update deliveries set completed_at = ? where id = ?",
                Timestamp.valueOf(completedAt),
                delivery.getId()
        );
        entityManager.clear();
        return foundOrder;
    }

    private Order saveDeliveredOrder(String key, LocalDateTime completedAt) {
        Order order = savePaidOrder(key);
        Order foundOrder = orderRepository.findWithBuyerAndProductById(order.getId()).orElseThrow();
        Delivery delivery = Delivery.start(foundOrder, "tracking-" + key);
        delivery.complete();
        deliveryRepository.save(delivery);
        entityManager.flush();
        jdbcTemplate.update(
                "update deliveries set completed_at = ? where id = ?",
                Timestamp.valueOf(completedAt),
                delivery.getId()
        );
        entityManager.clear();
        return foundOrder;
    }

    private Delivery createDeliveredDelivery(String key) {
        Member seller = Member.create("seller-" + key + "@example.com", "encoded-password", "seller-" + key);
        Member buyer = Member.create("buyer-" + key + "@example.com", "encoded-password", "buyer-" + key);
        Product product = Product.create(seller, "MacBook Pro " + key, "M3 laptop", 2_000_000L);
        Order order = Order.create(buyer, product);
        order.markPaid();
        Delivery delivery = Delivery.start(order, "tracking-" + key);
        delivery.complete();
        return delivery;
    }

    private Order findOrder(Order order) {
        entityManager.flush();
        entityManager.clear();
        return orderRepository.findWithBuyerAndProductById(order.getId()).orElseThrow();
    }
}
