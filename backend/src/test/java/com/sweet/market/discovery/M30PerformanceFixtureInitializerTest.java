package com.sweet.market.discovery;

import com.sweet.market.discovery.experiment.M30PerformanceFixtureInitializer;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Transactional
class M30PerformanceFixtureInitializerTest extends IntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private MemberRepository memberRepository;

    private M30PerformanceFixtureInitializer initializer;

    @BeforeEach
    void setUpInitializer() {
        initializer = new M30PerformanceFixtureInitializer(
                entityManager,
                passwordEncoder,
                jdbcTemplate,
                "m30-v1",
                310031L
        );
    }

    @Test
    void 빈_DB에_m30_v1_대표_fixture를_재현한다() {
        initializer.run();

        assertThat(count("stores", "type = 'BUSINESS' AND status = 'ACTIVE'")).isEqualTo(10);
        assertThat(count("products", "1=1")).isEqualTo(10_000);
        assertThat(count("inventories", "1=1")).isEqualTo(10_000);
        assertThat(count("promotion_campaigns", "lifecycle_status = 'SCHEDULED'")).isEqualTo(20);
        assertThat(count("coupon_campaigns", "lifecycle_status = 'SCHEDULED'")).isEqualTo(20);
        assertThat(count("wishlist_items", "1=1")).isEqualTo(50_000);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM product_view_events WHERE viewed_at BETWEEN ? AND ?",
                Integer.class,
                Timestamp.from(M30PerformanceFixtureInitializer.FIXTURE_INSTANT.minusSeconds(7 * 86_400L)),
                Timestamp.from(M30PerformanceFixtureInitializer.FIXTURE_INSTANT)
        )).isEqualTo(200_000);
    }

    @Test
    void 비어있지_않은_DB에서는_일부도_추가하지_않고_중단한다() {
        memberRepository.saveAndFlush(Member.create("occupied@example.com", "encoded", "occupied"));

        assertThatThrownBy(initializer::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("m30-v1 performance fixture requires an empty marketplace database");

        assertThat(count("members", "1=1")).isEqualTo(1);
        assertThat(count("stores", "1=1")).isZero();
        assertThat(count("products", "1=1")).isZero();
    }

    @Test
    void fixture는_고정_seed_clock_batch를_사용한다() {
        assertThat(M30PerformanceFixtureInitializer.FIXTURE_VERSION).isEqualTo("m30-v1");
        assertThat(M30PerformanceFixtureInitializer.RANDOM_SEED).isEqualTo(310031L);
        assertThat(M30PerformanceFixtureInitializer.FIXTURE_INSTANT)
                .isEqualTo(Instant.parse("2026-07-17T00:00:00Z"));
        assertThat(M30PerformanceFixtureInitializer.BATCH_SIZE).isEqualTo(500);
    }

    private int count(String table, String predicate) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + predicate,
                Integer.class
        );
    }
}
