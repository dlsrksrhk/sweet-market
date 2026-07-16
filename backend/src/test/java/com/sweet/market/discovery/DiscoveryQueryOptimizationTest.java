package com.sweet.market.discovery;

import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.discovery.api.ActiveEventResponse;
import com.sweet.market.discovery.query.DiscoveryQueryService;
import com.sweet.market.discovery.query.DiscoveryRepository;
import com.sweet.market.jpalab.QueryOptimizationTestSupport;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.wishlist.repository.WishlistItemRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryQueryOptimizationTest extends QueryOptimizationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private DataSource dataSource;

    @Test
    @Transactional
    void 익명_이벤트_조회는_개인상태와_조정이력을_읽지않는다() {
        Store store = activeStore();
        Product product = Product.create(store, "이벤트 공개 상품", "설명", 10_000L);
        product.replaceImages(List.of(ProductImage.local(
                "https://example.com/event.jpg", "event.jpg", "event.jpg", "image/jpeg", 100L, 0, true
        )));
        entityManager.persist(product);
        entityManager.flush();
        jdbcTemplate.update("""
                insert into promotion_campaigns (version, store_id, scope, discount_type, discount_value, priority, title,
                    start_at, end_at, lifecycle_status, created_at, updated_at)
                values (0, ?, 'STORE_WIDE', 'FIXED_AMOUNT', 1000, 1, '공개 이벤트',
                    current_timestamp - interval '1 hour', current_timestamp + interval '1 hour', 'SCHEDULED',
                    current_timestamp, current_timestamp)
                """, store.getId());
        flushAndClear();
        resetStatistics();
        SqlRecordingDataSource recordingDataSource = new SqlRecordingDataSource(new TransactionAwareDataSourceProxy(dataSource));

        List<ActiveEventResponse> events = new DiscoveryQueryService(
                new DiscoveryRepository(new NamedParameterJdbcTemplate(recordingDataSource)),
                wishlistItemRepository, cartItemRepository
        ).activeEvents();

        assertThat(events).hasSize(1);
        assertThat(queryCount()).isZero();
        assertThat(collectionFetchCount()).isZero();
        assertThat(recordingDataSource.executedSql()).hasSize(1).allSatisfy(sql -> assertThat(sql.toUpperCase(Locale.ROOT))
                .doesNotContain("MEMBER_COUPONS")
                .doesNotContain("INVENTORY_ADJUSTMENTS")
                .doesNotContain("COUNT("));
    }

    private Store activeStore() {
        Member owner = memberRepository.save(Member.create("discovery-budget@example.com", "encoded", "판매자"));
        Store store = Store.applyBusiness(owner, "이벤트 상점", "소개", "법인", "123-45-67890");
        store.approve();
        return storeRepository.save(store);
    }

    private static class SqlRecordingDataSource extends DelegatingDataSource {

        private final List<String> executedSql = new ArrayList<>();

        private SqlRecordingDataSource(DataSource targetDataSource) {
            super(targetDataSource);
        }

        @Override
        public Connection getConnection() throws SQLException {
            Connection connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        Object result = invoke(method, connection, arguments);
                        if ("prepareStatement".equals(method.getName()) && arguments[0] instanceof String sql) {
                            return recordingPreparedStatement((PreparedStatement) result, sql);
                        }
                        return result;
                    });
        }

        private PreparedStatement recordingPreparedStatement(PreparedStatement statement, String sql) {
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, arguments) -> {
                        if ("executeQuery".equals(method.getName())) {
                            executedSql.add(sql);
                        }
                        return invoke(method, statement, arguments);
                    });
        }

        private Object invoke(java.lang.reflect.Method method, Object target, Object[] arguments) throws Throwable {
            try {
                return method.invoke(target, arguments);
            } catch (InvocationTargetException exception) {
                throw exception.getCause();
            }
        }

        private List<String> executedSql() {
            return executedSql;
        }
    }
}
