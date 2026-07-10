package com.sweet.market.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
import com.sweet.market.store.repository.StoreRepository;
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
    private StoreRepository storeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private DemoDataInitializer initializer() {
        return new DemoDataInitializer(
                memberRepository,
                productRepository,
                orderRepository,
                paymentRepository,
                deliveryRepository,
                settlementRepository,
                storeRepository,
                passwordEncoder,
                jdbcTemplate
        );
    }

    @Test
    void 반복_실행해도_데모_데이터를_중복_생성하지_않는다() {
        DemoDataInitializer initializer = initializer();

        transactionTemplate.executeWithoutResult(status -> initializer.run());
        Counts firstCounts = counts();

        transactionTemplate.executeWithoutResult(status -> initializer.run());
        Counts secondCounts = counts();

        assertThat(secondCounts).isEqualTo(firstCounts);
        assertThat(firstCounts.members()).isGreaterThanOrEqualTo(35);
        assertThat(firstCounts.products()).isGreaterThanOrEqualTo(120);
        assertThat(firstCounts.orders()).isGreaterThanOrEqualTo(240);
        assertThat(firstCounts.payments()).isGreaterThanOrEqualTo(190);
        assertThat(firstCounts.deliveries()).isGreaterThanOrEqualTo(150);
        assertThat(firstCounts.settlements()).isGreaterThanOrEqualTo(70);

        assertDemoAccount("admin@example.com", MemberRole.ADMIN);
        for (String email : List.of(
                "seller1@example.com",
                "seller2@example.com",
                "seller10@example.com",
                "buyer1@example.com",
                "buyer2@example.com",
                "buyer24@example.com"
        )) {
            assertDemoAccount(email, MemberRole.MEMBER);
        }
    }

    @Test
    void 데모_데이터는_관리자_필터에_필요한_상태들을_모두_포함한다() {
        DemoDataInitializer initializer = initializer();

        transactionTemplate.executeWithoutResult(status -> initializer.run());

        assertStatuses("products", "ON_SALE", "RESERVED", "SOLD_OUT", "HIDDEN");
        assertStatuses("orders", "CREATED", "PAID", "SHIPPING", "DELIVERED", "CONFIRMED", "CANCELED");
        assertStatuses("payments", "APPROVED", "CANCELED");
        assertStatuses("deliveries", "SHIPPING", "DELIVERED");
        assertStatuses("settlements", "COMPLETED", "READY", "FAILED");
    }

    @Test
    void 데모_주문은_최근_180일_범위에_분포한다() {
        DemoDataInitializer initializer = initializer();

        transactionTemplate.executeWithoutResult(status -> initializer.run());

        LocalDate today = LocalDate.now();
        LocalDateTime minOrderedAt = jdbcTemplate.queryForObject(
                "select min(ordered_at) from orders",
                LocalDateTime.class
        );
        LocalDateTime maxOrderedAt = jdbcTemplate.queryForObject(
                "select max(ordered_at) from orders",
                LocalDateTime.class
        );
        Long distinctOrderDays = jdbcTemplate.queryForObject(
                "select count(distinct cast(ordered_at as date)) from orders",
                Long.class
        );

        assertThat(minOrderedAt).isNotNull();
        assertThat(maxOrderedAt).isNotNull();
        assertThat(minOrderedAt.toLocalDate()).isAfterOrEqualTo(today.minusDays(180));
        assertThat(maxOrderedAt.toLocalDate()).isBeforeOrEqualTo(today);
        assertThat(distinctOrderDays).isGreaterThanOrEqualTo(90);
    }

    @Test
    void 데모_데이터는_정산과_자동구매확정_시나리오를_포함한다() {
        DemoDataInitializer initializer = initializer();

        transactionTemplate.executeWithoutResult(status -> initializer.run());

        assertThat(deliveredAutoConfirmCandidateCount()).isGreaterThanOrEqualTo(10);
        assertThat(confirmedSettledOrderCount()).isGreaterThanOrEqualTo(60);
        assertThat(confirmedUnsettledEligibleOrderCount()).isGreaterThanOrEqualTo(20);
        assertThat(confirmedRecentUnsettledOrderCount()).isGreaterThanOrEqualTo(8);
        assertThat(settlementStatusCount("COMPLETED")).isGreaterThanOrEqualTo(60);
        assertThat(settlementStatusCount("READY")).isBetween(1L, 5L);
        assertThat(settlementStatusCount("FAILED")).isBetween(1L, 5L);
    }

    @Test
    void 데모_생애주기_시각은_미래가_아니다() {
        DemoDataInitializer initializer = initializer();

        transactionTemplate.executeWithoutResult(status -> initializer.run());

        assertNoFutureTimestamp("orders", "ordered_at");
        assertNoFutureTimestamp("orders", "canceled_at");
        assertNoFutureTimestamp("orders", "confirmed_at");
        assertNoFutureTimestamp("payments", "approved_at");
        assertNoFutureTimestamp("payments", "canceled_at");
        assertNoFutureTimestamp("deliveries", "started_at");
        assertNoFutureTimestamp("deliveries", "completed_at");
        assertNoFutureTimestamp("settlements", "settled_at");
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

    private void assertStatuses(String tableName, String... expectedStatuses) {
        List<String> statuses = jdbcTemplate.queryForList(
                "select distinct status from " + tableName,
                String.class
        );

        assertThat(statuses).contains(expectedStatuses);
    }

    private void assertNoFutureTimestamp(String tableName, String columnName) {
        Long futureCount = jdbcTemplate.queryForObject(
                "select count(*) from " + tableName + " where " + columnName + " is not null and " + columnName + " > ?",
                Long.class,
                LocalDateTime.now()
        );

        assertThat(futureCount).isZero();
    }

    private Long deliveredAutoConfirmCandidateCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from deliveries d
                join orders o on o.id = d.order_id
                where d.status = 'DELIVERED'
                  and o.status = 'DELIVERED'
                  and d.completed_at < ?
                """, Long.class, LocalDateTime.now().minusDays(7));
    }

    private Long confirmedSettledOrderCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from orders o
                join settlements s on s.order_id = o.id
                where o.status = 'CONFIRMED'
                """, Long.class);
    }

    private Long confirmedUnsettledEligibleOrderCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from orders o
                left join settlements s on s.order_id = o.id
                where o.status = 'CONFIRMED'
                  and o.confirmed_at < ?
                  and s.id is null
                """, Long.class, LocalDateTime.now().minusDays(7));
    }

    private Long confirmedRecentUnsettledOrderCount() {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from orders o
                left join settlements s on s.order_id = o.id
                where o.status = 'CONFIRMED'
                  and o.confirmed_at >= ?
                  and s.id is null
                """, Long.class, LocalDateTime.now().minusDays(7));
    }

    private Long settlementStatusCount(String status) {
        return jdbcTemplate.queryForObject("""
                select count(*)
                from settlements
                where status = ?
                """, Long.class, status);
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
