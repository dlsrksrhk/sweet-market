package com.sweet.market.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import com.sweet.market.delivery.repository.DeliveryRepository;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.domain.MemberRole;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.order.repository.OrderRepository;
import com.sweet.market.payment.repository.PaymentRepository;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.settlement.repository.SettlementRepository;
import com.sweet.market.support.IntegrationTestSupport;

class DemoDataInitializerTest extends IntegrationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 반복_실행해도_데모_데이터를_중복_생성하지_않는다() {
        DemoDataInitializer initializer = new DemoDataInitializer(
                memberRepository,
                productRepository,
                orderRepository,
                paymentRepository,
                deliveryRepository,
                settlementRepository,
                passwordEncoder
        );

        transactionTemplate.executeWithoutResult(status -> initializer.run());
        Counts firstCounts = counts();

        transactionTemplate.executeWithoutResult(status -> initializer.run());
        Counts secondCounts = counts();

        assertThat(secondCounts).isEqualTo(firstCounts);
        assertThat(firstCounts.members()).isGreaterThanOrEqualTo(5);
        assertThat(firstCounts.products()).isGreaterThanOrEqualTo(6);
        assertThat(firstCounts.orders()).isGreaterThanOrEqualTo(6);
        assertThat(firstCounts.payments()).isGreaterThanOrEqualTo(5);
        assertThat(firstCounts.deliveries()).isGreaterThanOrEqualTo(4);
        assertThat(firstCounts.settlements()).isGreaterThanOrEqualTo(1);
        assertThat(onSaleProductCount()).isGreaterThanOrEqualTo(1);
        assertThat(confirmedUnsettledOrderCount()).isGreaterThanOrEqualTo(1);

        assertDemoAccount("admin@example.com", MemberRole.ADMIN);
        for (String email : List.of(
                "seller1@example.com",
                "seller2@example.com",
                "buyer1@example.com",
                "buyer2@example.com"
        )) {
            assertDemoAccount(email, MemberRole.MEMBER);
        }
    }

    private Counts counts() {
        return new Counts(
                memberRepository.count(),
                productRepository.count(),
                orderRepository.count(),
                paymentRepository.count(),
                deliveryRepository.count(),
                settlementRepository.count()
        );
    }

    private Long confirmedUnsettledOrderCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from orders o
                left join settlements s on s.order_id = o.id
                where o.status = 'CONFIRMED'
                  and s.id is null
                """, Long.class);
    }

    private Long onSaleProductCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from products
                where status = 'ON_SALE'
                """, Long.class);
    }

    private void assertDemoAccount(String email, MemberRole role) {
        Member member = memberRepository.findByEmail(email).orElseThrow();

        assertThat(member.getRole()).isEqualTo(role);
        assertThat(passwordEncoder.matches("password123", member.getPassword())).isTrue();
    }

    private record Counts(
            long members,
            long products,
            long orders,
            long payments,
            long deliveries,
            long settlements
    ) {
    }
}
