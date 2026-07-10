package com.sweet.market.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.delivery.domain.Delivery;
import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.domain.Order;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.domain.Payment;
import com.sweet.market.payment.repository.PaymentRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.settlement.domain.Settlement;
import com.sweet.market.settlement.repository.SettlementRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;

@Component
@Profile({"local", "dev"})
public class DemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "password123";
    private static final int SELLER_COUNT = 10;
    private static final int BUYER_COUNT = 24;
    private static final int CATALOG_PRODUCT_COUNT = 120;
    private static final int CREATED_ORDER_COUNT = 25;
    private static final int PAID_ORDER_COUNT = 30;
    private static final int SHIPPING_ORDER_COUNT = 30;
    private static final int DELIVERED_OLD_ORDER_COUNT = 25;
    private static final int DELIVERED_RECENT_ORDER_COUNT = 15;
    private static final int CONFIRMED_SETTLED_ORDER_COUNT = 70;
    private static final int CONFIRMED_UNSETTLED_ELIGIBLE_ORDER_COUNT = 25;
    private static final int CONFIRMED_UNSETTLED_RECENT_ORDER_COUNT = 10;
    private static final int CANCELED_CREATED_ORDER_COUNT = 10;
    private static final int CANCELED_PAID_ORDER_COUNT = 10;

    private static final List<String> PRODUCT_GROUPS = List.of(
            "Electronics",
            "Kitchen",
            "Fashion",
            "Books",
            "Sports",
            "Home",
            "Hobby",
            "Office"
    );

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryRepository deliveryRepository;
    private final SettlementRepository settlementRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public DemoDataInitializer(
            MemberRepository memberRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            DeliveryRepository deliveryRepository,
            SettlementRepository settlementRepository,
            StoreRepository storeRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate
    ) {
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.deliveryRepository = deliveryRepository;
        this.settlementRepository = settlementRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        run();
    }

    @Transactional
    public void run() {
        if (memberRepository.existsByEmail("admin@example.com")) {
            return;
        }

        LocalDate today = LocalDate.now();
        String encodedPassword = passwordEncoder.encode(DEMO_PASSWORD);
        memberRepository.save(Member.createAdmin("admin@example.com", encodedPassword, "admin"));
        List<Member> sellers = createSellers(encodedPassword);
        List<Member> buyers = createBuyers(encodedPassword);

        createCatalogProducts(sellers);
        createOrderTimeline(sellers, buyers, today);
    }

    private List<Member> createSellers(String encodedPassword) {
        List<Member> sellers = new ArrayList<>();
        for (int index = 1; index <= SELLER_COUNT; index++) {
            Member seller = memberRepository.save(Member.create(
                    "seller" + index + "@example.com",
                    encodedPassword,
                    "seller" + index
            ));
            storeRepository.save(Store.createPersonal(seller, seller.getNickname() + "의 상점", ""));
            sellers.add(seller);
        }
        return sellers;
    }

    private List<Member> createBuyers(String encodedPassword) {
        List<Member> buyers = new ArrayList<>();
        for (int index = 1; index <= BUYER_COUNT; index++) {
            buyers.add(memberRepository.save(Member.create(
                    "buyer" + index + "@example.com",
                    encodedPassword,
                    "buyer" + index
            )));
        }
        return buyers;
    }

    private void createCatalogProducts(List<Member> sellers) {
        for (int index = 1; index <= CATALOG_PRODUCT_COUNT; index++) {
            Member seller = pick(sellers, index);
            String group = PRODUCT_GROUPS.get((index - 1) % PRODUCT_GROUPS.size());
            Product product = productRepository.save(Product.create(
                    personalStore(seller),
                    group + " Demo Item " + index,
                    "Local demo " + group.toLowerCase() + " listing number " + index + ".",
                    priceFor(index)
            ));

            if (index % 12 == 0) {
                product.hide();
            }
        }
    }

    private Member pick(List<Member> members, int oneBasedIndex) {
        return members.get((oneBasedIndex - 1) % members.size());
    }

    private long priceFor(int index) {
        return 8_000L + (long) (index % 37) * 3_000L;
    }

    private void createOrderTimeline(List<Member> sellers, List<Member> buyers, LocalDate today) {
        List<OrderScenario> scenarios = List.of(
                new OrderScenario(DemoOrderStatus.CREATED, CREATED_ORDER_COUNT, 179, false),
                new OrderScenario(DemoOrderStatus.PAID, PAID_ORDER_COUNT, 154, false),
                new OrderScenario(DemoOrderStatus.SHIPPING, SHIPPING_ORDER_COUNT, 129, false),
                new OrderScenario(DemoOrderStatus.DELIVERED, DELIVERED_OLD_ORDER_COUNT, 104, true),
                new OrderScenario(DemoOrderStatus.DELIVERED, DELIVERED_RECENT_ORDER_COUNT, 12, false),
                new OrderScenario(DemoOrderStatus.CONFIRMED, CONFIRMED_SETTLED_ORDER_COUNT, 96, false),
                new OrderScenario(DemoOrderStatus.CONFIRMED, CONFIRMED_UNSETTLED_ELIGIBLE_ORDER_COUNT, 46, false),
                new OrderScenario(DemoOrderStatus.CONFIRMED, CONFIRMED_UNSETTLED_RECENT_ORDER_COUNT, 13, false),
                new OrderScenario(DemoOrderStatus.CANCELED_CREATED, CANCELED_CREATED_ORDER_COUNT, 33, false),
                new OrderScenario(DemoOrderStatus.CANCELED_PAID, CANCELED_PAID_ORDER_COUNT, 24, false)
        );

        int sequence = 1;
        for (OrderScenario scenario : scenarios) {
            for (int index = 0; index < scenario.count(); index++) {
                int offsetDays = Math.max(0, scenario.startOffsetDays() - index);
                if (scenario.status() == DemoOrderStatus.DELIVERED && !scenario.oldDeliveredCandidate()) {
                    offsetDays = Math.max(3, offsetDays);
                }
                LocalDate orderDate = today.minusDays(offsetDays);
                createScenarioOrder(sellers, buyers, scenario, sequence, orderDate, today);
                sequence++;
            }
        }
    }

    private void createScenarioOrder(
            List<Member> sellers,
            List<Member> buyers,
            OrderScenario scenario,
            int sequence,
            LocalDate orderDate,
            LocalDate today
    ) {
        Member seller = pick(sellers, sequence);
        Member buyer = pick(buyers, sequence * 3);
        Product product = createProduct(
                seller,
                "Timeline " + scenario.status().name() + " Product " + sequence,
                priceFor(sequence + CATALOG_PRODUCT_COUNT)
        );
        Order order = orderRepository.save(Order.create(buyer, product));
        LocalDateTime orderedAt = orderDate.atTime(9 + sequence % 8, 15);
        updateOrderOrderedAt(order, orderedAt);
        Payment payment = null;
        Delivery delivery = null;
        Settlement settlement = null;
        LocalDateTime completedAt = null;

        switch (scenario.status()) {
            case CREATED -> {
            }
            case PAID -> payment = createApprovedPayment(order, sequence, orderedAt.plusHours(1));
            case SHIPPING -> {
                payment = createApprovedPayment(order, sequence, orderedAt.plusHours(1));
                delivery = createShippingDelivery(order, sequence, orderedAt.plusDays(1));
            }
            case DELIVERED -> {
                payment = createApprovedPayment(order, sequence, orderedAt.plusHours(1));
                delivery = createShippingDelivery(order, sequence, orderedAt.plusDays(1));
                delivery.complete();
                completedAt = scenario.oldDeliveredCandidate()
                        ? orderedAt.plusDays(3)
                        : orderedAt.plusDays(2);
                updateDeliveredTimestamps(delivery, orderedAt.plusDays(1), completedAt);
            }
            case CONFIRMED -> {
                payment = createApprovedPayment(order, sequence, orderedAt.plusHours(1));
                delivery = createShippingDelivery(order, sequence, orderedAt.plusDays(1));
                delivery.complete();
                completedAt = orderedAt.plusDays(3);
                order.confirm();
                updateDeliveredTimestamps(delivery, orderedAt.plusDays(1), completedAt);
                updateOrderConfirmedAt(order, confirmedAtForScenario(sequence, completedAt, today));
                if (sequence <= CREATED_ORDER_COUNT
                        + PAID_ORDER_COUNT
                        + SHIPPING_ORDER_COUNT
                        + DELIVERED_OLD_ORDER_COUNT
                        + DELIVERED_RECENT_ORDER_COUNT
                        + CONFIRMED_SETTLED_ORDER_COUNT) {
                    settlement = settlementRepository.save(Settlement.create(order));
                    updateSettlementSettledAt(settlement, completedAt.plusDays(3));
                    updateExceptionalSettlementStatus(settlement, sequence);
                }
            }
            case CANCELED_CREATED -> {
                order.cancel();
                updateOrderCanceledAt(order, orderedAt.plusHours(2));
            }
            case CANCELED_PAID -> {
                payment = createApprovedPayment(order, sequence, orderedAt.plusHours(1));
                payment.cancel();
                updatePaymentCanceledAt(payment, orderedAt.plusHours(3));
                updateOrderCanceledAt(order, orderedAt.plusHours(3));
            }
        }

        flushTimelineEntities();
        backfillScenarioTimestamps(scenario, order, payment, delivery, settlement, sequence, orderedAt, completedAt, today);
    }

    private Store personalStore(Member seller) {
        return storeRepository.findPersonalByOwnerMemberId(seller.getId())
                .orElseThrow();
    }

    private void flushTimelineEntities() {
        productRepository.flush();
        orderRepository.flush();
        paymentRepository.flush();
        deliveryRepository.flush();
        settlementRepository.flush();
    }

    private void backfillScenarioTimestamps(
            OrderScenario scenario,
            Order order,
            Payment payment,
            Delivery delivery,
            Settlement settlement,
            int sequence,
            LocalDateTime orderedAt,
            LocalDateTime completedAt,
            LocalDate today
    ) {
        updateOrderOrderedAt(order, orderedAt);
        if (payment != null) {
            updatePaymentApprovedAt(payment, orderedAt.plusHours(1));
        }
        if (delivery != null && completedAt == null) {
            updateDeliveryStartedAt(delivery, orderedAt.plusDays(1));
        }
        if (delivery != null && completedAt != null) {
            updateDeliveredTimestamps(delivery, orderedAt.plusDays(1), completedAt);
        }

        switch (scenario.status()) {
            case CREATED, PAID, SHIPPING, DELIVERED -> {
            }
            case CONFIRMED -> {
                updateOrderConfirmedAt(order, confirmedAtForScenario(sequence, completedAt, today));
                if (settlement != null) {
                    updateSettlementSettledAt(settlement, completedAt.plusDays(3));
                    updateExceptionalSettlementStatus(settlement, sequence);
                }
            }
            case CANCELED_CREATED -> updateOrderCanceledAt(order, orderedAt.plusHours(2));
            case CANCELED_PAID -> {
                updatePaymentCanceledAt(payment, orderedAt.plusHours(3));
                updateOrderCanceledAt(order, orderedAt.plusHours(3));
            }
        }
    }

    private Payment createApprovedPayment(Order order, int sequence, LocalDateTime approvedAt) {
        Payment payment = paymentRepository.save(Payment.approve(order, "demo-payment-" + sequence));
        updatePaymentApprovedAt(payment, approvedAt);
        return payment;
    }

    private Delivery createShippingDelivery(Order order, int sequence, LocalDateTime startedAt) {
        Delivery delivery = deliveryRepository.save(Delivery.start(order, "DEMO-TRACK-" + sequence));
        updateDeliveryStartedAt(delivery, startedAt);
        return delivery;
    }

    private void updateOrderOrderedAt(Order order, LocalDateTime orderedAt) {
        jdbcTemplate.update("update orders set ordered_at = ? where id = ?", orderedAt, order.getId());
    }

    private void updateOrderCanceledAt(Order order, LocalDateTime canceledAt) {
        jdbcTemplate.update("update orders set canceled_at = ? where id = ?", canceledAt, order.getId());
    }

    private void updateOrderConfirmedAt(Order order, LocalDateTime confirmedAt) {
        jdbcTemplate.update("update orders set confirmed_at = ? where id = ?", confirmedAt, order.getId());
    }

    private LocalDateTime confirmedAtForScenario(int sequence, LocalDateTime completedAt, LocalDate today) {
        LocalDateTime confirmedAt = completedAt.plusDays(1);
        if (!isRecentUnsettledConfirmedSequence(sequence)) {
            return confirmedAt;
        }

        LocalDateTime recentConfirmedAt = today.minusDays(6).atTime(12, 0);
        LocalDateTime latestHistoricalAt = today.atStartOfDay();
        if (confirmedAt.isBefore(recentConfirmedAt)) {
            return recentConfirmedAt;
        }
        if (confirmedAt.isAfter(latestHistoricalAt)) {
            return latestHistoricalAt;
        }
        return confirmedAt;
    }

    private boolean isRecentUnsettledConfirmedSequence(int sequence) {
        int recentUnsettledStartSequence = CREATED_ORDER_COUNT
                + PAID_ORDER_COUNT
                + SHIPPING_ORDER_COUNT
                + DELIVERED_OLD_ORDER_COUNT
                + DELIVERED_RECENT_ORDER_COUNT
                + CONFIRMED_SETTLED_ORDER_COUNT
                + CONFIRMED_UNSETTLED_ELIGIBLE_ORDER_COUNT
                + 1;
        int recentUnsettledEndSequence = recentUnsettledStartSequence
                + CONFIRMED_UNSETTLED_RECENT_ORDER_COUNT
                - 1;
        return sequence >= recentUnsettledStartSequence && sequence <= recentUnsettledEndSequence;
    }

    private void updatePaymentApprovedAt(Payment payment, LocalDateTime approvedAt) {
        jdbcTemplate.update("update payments set approved_at = ? where id = ?", approvedAt, payment.getId());
    }

    private void updatePaymentCanceledAt(Payment payment, LocalDateTime canceledAt) {
        jdbcTemplate.update("update payments set canceled_at = ? where id = ?", canceledAt, payment.getId());
    }

    private void updateDeliveryStartedAt(Delivery delivery, LocalDateTime startedAt) {
        jdbcTemplate.update("update deliveries set started_at = ? where id = ?", startedAt, delivery.getId());
    }

    private void updateDeliveredTimestamps(Delivery delivery, LocalDateTime startedAt, LocalDateTime completedAt) {
        jdbcTemplate.update(
                "update deliveries set started_at = ?, completed_at = ? where id = ?",
                startedAt,
                completedAt,
                delivery.getId()
        );
    }

    private void updateSettlementSettledAt(Settlement settlement, LocalDateTime settledAt) {
        jdbcTemplate.update("update settlements set settled_at = ? where id = ?", settledAt, settlement.getId());
    }

    private void updateExceptionalSettlementStatus(Settlement settlement, int sequence) {
        if (sequence % 35 == 0) {
            jdbcTemplate.update("update settlements set status = 'READY' where id = ?", settlement.getId());
        } else if (sequence % 37 == 0) {
            jdbcTemplate.update("update settlements set status = 'FAILED' where id = ?", settlement.getId());
        }
    }

    private Product createProduct(Member seller, String title, long price) {
        return productRepository.save(Product.create(
                personalStore(seller),
                title,
                "Local demo product for " + title + ".",
                price
        ));
    }

    private record OrderScenario(
            DemoOrderStatus status,
            int count,
            int startOffsetDays,
            boolean oldDeliveredCandidate
    ) {
    }

    private enum DemoOrderStatus {
        CREATED,
        PAID,
        SHIPPING,
        DELIVERED,
        CONFIRMED,
        CANCELED_CREATED,
        CANCELED_PAID
    }
}
