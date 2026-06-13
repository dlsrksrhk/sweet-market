package com.sweet.market.demo;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
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

@Component
@Profile({"local", "dev"})
public class DemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "password123";

    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final DeliveryRepository deliveryRepository;
    private final SettlementRepository settlementRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(
            MemberRepository memberRepository,
            ProductRepository productRepository,
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            DeliveryRepository deliveryRepository,
            SettlementRepository settlementRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.memberRepository = memberRepository;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.deliveryRepository = deliveryRepository;
        this.settlementRepository = settlementRepository;
        this.passwordEncoder = passwordEncoder;
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

        String encodedPassword = passwordEncoder.encode(DEMO_PASSWORD);
        memberRepository.save(Member.createAdmin("admin@example.com", encodedPassword, "admin"));
        Member seller1 = memberRepository.save(Member.create("seller1@example.com", encodedPassword, "seller1"));
        Member seller2 = memberRepository.save(Member.create("seller2@example.com", encodedPassword, "seller2"));
        Member buyer1 = memberRepository.save(Member.create("buyer1@example.com", encodedPassword, "buyer1"));
        Member buyer2 = memberRepository.save(Member.create("buyer2@example.com", encodedPassword, "buyer2"));

        createVisibleProducts(seller1, seller2);
        createCreatedOrder(buyer1, seller1);
        createPaidOrder(buyer2, seller1);
        createShippingOrder(buyer1, seller2);
        createDeliveredOrder(buyer2, seller2);
        createConfirmedUnsettledOrder(buyer1, seller1);
        createConfirmedSettledOrder(buyer2, seller2);
    }

    private void createVisibleProducts(Member seller1, Member seller2) {
        productRepository.save(Product.create(
                seller1,
                "Demo Wireless Keyboard",
                "A visible local demo listing ready for purchase.",
                45_000L
        ));
        productRepository.save(Product.create(
                seller2,
                "Demo Ceramic Mug",
                "A second visible local demo listing ready for purchase.",
                18_000L
        ));
    }

    private void createCreatedOrder(Member buyer, Member seller) {
        Product product = createProduct(seller, "Demo CREATED Order Product", 30_000L);
        Order order = Order.create(buyer, product);

        orderRepository.save(order);
    }

    private void createPaidOrder(Member buyer, Member seller) {
        Order order = createOrder(buyer, seller, "Demo PAID Order Product", 55_000L);
        Payment payment = Payment.approve(order, "demo-payment-paid");

        paymentRepository.save(payment);
    }

    private void createShippingOrder(Member buyer, Member seller) {
        Order order = createPaidDemoOrder(buyer, seller, "Demo SHIPPING Order Product", 77_000L, "demo-payment-shipping");
        Delivery delivery = Delivery.start(order, "DEMO-TRACK-SHIPPING");

        deliveryRepository.save(delivery);
    }

    private void createDeliveredOrder(Member buyer, Member seller) {
        Delivery delivery = createShippingDemoDelivery(
                buyer,
                seller,
                "Demo DELIVERED Order Product",
                88_000L,
                "demo-payment-delivered",
                "DEMO-TRACK-DELIVERED"
        );
        delivery.complete();
    }

    private void createConfirmedUnsettledOrder(Member buyer, Member seller) {
        Order order = createDeliveredDemoOrder(
                buyer,
                seller,
                "Demo CONFIRMED Unsettled Product",
                99_000L,
                "demo-payment-confirmed-unsettled",
                "DEMO-TRACK-CONFIRMED-UNSETTLED"
        );

        order.confirm();
    }

    private void createConfirmedSettledOrder(Member buyer, Member seller) {
        Order order = createDeliveredDemoOrder(
                buyer,
                seller,
                "Demo CONFIRMED Settled Product",
                120_000L,
                "demo-payment-confirmed-settled",
                "DEMO-TRACK-CONFIRMED-SETTLED"
        );
        order.confirm();

        settlementRepository.save(Settlement.create(order));
    }

    private Order createDeliveredDemoOrder(
            Member buyer,
            Member seller,
            String productTitle,
            long price,
            String externalPaymentId,
            String trackingNumber
    ) {
        Delivery delivery = createShippingDemoDelivery(buyer, seller, productTitle, price, externalPaymentId, trackingNumber);
        delivery.complete();
        return delivery.getOrder();
    }

    private Delivery createShippingDemoDelivery(
            Member buyer,
            Member seller,
            String productTitle,
            long price,
            String externalPaymentId,
            String trackingNumber
    ) {
        Order order = createPaidDemoOrder(buyer, seller, productTitle, price, externalPaymentId);
        Delivery delivery = Delivery.start(order, trackingNumber);

        return deliveryRepository.save(delivery);
    }

    private Order createPaidDemoOrder(
            Member buyer,
            Member seller,
            String productTitle,
            long price,
            String externalPaymentId
    ) {
        Order order = createOrder(buyer, seller, productTitle, price);
        Payment payment = Payment.approve(order, externalPaymentId);

        paymentRepository.save(payment);
        return order;
    }

    private Order createOrder(Member buyer, Member seller, String productTitle, long price) {
        Product product = createProduct(seller, productTitle, price);
        Order order = Order.create(buyer, product);

        return orderRepository.save(order);
    }

    private Product createProduct(Member seller, String title, long price) {
        return productRepository.save(Product.create(
                seller,
                title,
                "Local demo product for " + title + ".",
                price
        ));
    }
}
