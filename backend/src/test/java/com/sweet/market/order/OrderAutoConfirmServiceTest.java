package com.sweet.market.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
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

    private Order findOrder(Order order) {
        entityManager.flush();
        entityManager.clear();
        return orderRepository.findWithBuyerAndProductById(order.getId()).orElseThrow();
    }
}
