package com.sweet.market.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.sweet.market.cart.domain.CartItem;
import com.sweet.market.cart.repository.CartItemRepository;
import com.sweet.market.catalog.api.CatalogSearchRequest;
import com.sweet.market.catalog.api.CatalogSearchResponse;
import com.sweet.market.catalog.query.CatalogCursorCodec;
import com.sweet.market.catalog.query.CatalogSearchQueryService;
import com.sweet.market.catalog.query.CatalogSearchRepository;
import com.sweet.market.jpalab.QueryOptimizationTestSupport;
import com.sweet.market.member.domain.Member;
import com.sweet.market.member.repository.MemberRepository;
import com.sweet.market.product.domain.Product;
import com.sweet.market.product.domain.ProductImage;
import com.sweet.market.product.repository.ProductRepository;
import com.sweet.market.store.domain.Store;
import com.sweet.market.store.repository.StoreRepository;
import com.sweet.market.wishlist.domain.WishlistItem;
import com.sweet.market.wishlist.repository.WishlistItemRepository;

import javax.sql.DataSource;

@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "com.sweet.market.catalog.CatalogQueryOptimizationTest$SqlStatementInspector"
})
class CatalogQueryOptimizationTest extends QueryOptimizationTestSupport {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private WishlistItemRepository wishlistItemRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CatalogCursorCodec catalogCursorCodec;

    @Autowired
    private DataSource dataSource;

    @Test
    @Transactional
    void 인증된_카탈로그_페이지는_조정이력을_읽지않고_세_쿼리_이내로_조회한다() {
        Fixture fixture = 카탈로그_픽스처를_저장한다(20);
        entityManager.persist(WishlistItem.create(entityManager.getReference(Member.class, fixture.viewerId()),
                entityManager.getReference(Product.class, fixture.productIds().getLast())));
        entityManager.persist(CartItem.create(entityManager.getReference(Member.class, fixture.viewerId()),
                entityManager.getReference(Product.class, fixture.productIds().get(fixture.productIds().size() - 2))));
        flushAndClear();
        쿼리_측정을_초기화한다();
        SqlRecordingDataSource recordingDataSource = new SqlRecordingDataSource(
                new TransactionAwareDataSourceProxy(dataSource)
        );

        CatalogSearchResponse page = 기록_서비스(recordingDataSource).search(
                fixture.viewerId(), 기본_요청(), null);

        assertThat(page.content()).hasSize(12);
        assertThat(page.content()).allSatisfy(product -> assertThat(product.representativeImageUrl()).isNotBlank());
        assertThat(page.content()).anySatisfy(product -> assertThat(product.wishlisted()).isTrue());
        assertThat(page.content()).anySatisfy(product -> assertThat(product.carted()).isTrue());
        assertThat(queryCount()).isLessThanOrEqualTo(2L);
        assertThat(collectionFetchCount()).isZero();
        모든_쿼리_예산과_금지_SQL을_검증한다(recordingDataSource, 3);
    }

    @Test
    @Transactional
    void 익명_카탈로그_페이지는_단일_카탈로그_쿼리와_컬렉션_조회없이_조회한다() {
        카탈로그_픽스처를_저장한다(20);
        flushAndClear();
        쿼리_측정을_초기화한다();
        SqlRecordingDataSource recordingDataSource = new SqlRecordingDataSource(
                new TransactionAwareDataSourceProxy(dataSource)
        );

        CatalogSearchResponse page = 기록_서비스(recordingDataSource).search(null, 기본_요청(), null);

        assertThat(page.content()).hasSize(12);
        assertThat(page.content()).allSatisfy(product -> assertThat(product.representativeImageUrl()).isNotBlank());
        assertThat(queryCount()).isZero();
        assertThat(collectionFetchCount()).isZero();
        모든_쿼리_예산과_금지_SQL을_검증한다(recordingDataSource, 1);
    }

    private CatalogSearchQueryService 기록_서비스(SqlRecordingDataSource recordingDataSource) {
        return new CatalogSearchQueryService(
                new CatalogSearchRepository(new NamedParameterJdbcTemplate(recordingDataSource)),
                catalogCursorCodec,
                storeRepository,
                wishlistItemRepository,
                cartItemRepository
        );
    }

    private CatalogSearchRequest 기본_요청() {
        return new CatalogSearchRequest(null, null, null, null, null, null, null, null, null, null, null);
    }

    private Fixture 카탈로그_픽스처를_저장한다(int productCount) {
        Member owner = memberRepository.save(Member.create(
                "catalog-budget-owner@example.com", "encoded-password", "카탈로그 예산 판매자"
        ));
        Member viewer = memberRepository.save(Member.create(
                "catalog-budget-viewer@example.com", "encoded-password", "카탈로그 예산 구매자"
        ));
        Store store = Store.applyBusiness(owner, "카탈로그 예산 상점", "조회 예산 픽스처", "카탈로그 법인", "123-45-67890");
        store.approve();
        storeRepository.save(store);
        List<Long> productIds = new ArrayList<>();
        for (int index = 1; index <= productCount; index++) {
            Product product = Product.create(store, "카탈로그 상품 " + index, "카탈로그 설명 " + index, 10_000L + index);
            product.replaceImages(List.of(
                    이미지("fallback-" + index + ".jpg", 0, false),
                    이미지("representative-" + index + ".jpg", 1, true)
            ));
            product = productRepository.save(product);
            productIds.add(product.getId());
        }
        return new Fixture(viewer.getId(), productIds);
    }

    private ProductImage 이미지(String fileName, int sortOrder, boolean representative) {
        return ProductImage.local(
                "https://example.com/" + fileName,
                fileName,
                fileName,
                "image/jpeg",
                100L,
                sortOrder,
                representative
        );
    }

    private void 쿼리_측정을_초기화한다() {
        resetStatistics();
        SqlStatementInspector.clear();
    }

    private void 모든_쿼리_예산과_금지_SQL을_검증한다(SqlRecordingDataSource recordingDataSource, int maxTotalStatements) {
        assertThat(recordingDataSource.executedSql()).hasSize(1);
        List<String> executedSql = new ArrayList<>(recordingDataSource.executedSql());
        executedSql.addAll(SqlStatementInspector.executedSql());
        assertThat(executedSql).hasSizeLessThanOrEqualTo(maxTotalStatements);
        assertThat(executedSql).allSatisfy(sql -> assertThat(sql.toUpperCase(Locale.ROOT))
                .doesNotContain("COUNT(")
                .doesNotContain("INVENTORY_ADJUSTMENTS"));
    }

    private record Fixture(Long viewerId, List<Long> productIds) {
    }

    private static class SqlRecordingDataSource extends DelegatingDataSource {

        private final List<String> executedSql = new ArrayList<>();

        private SqlRecordingDataSource(DataSource targetDataSource) {
            super(targetDataSource);
        }

        @Override
        public Connection getConnection() throws SQLException {
            return recordingConnection(super.getConnection());
        }

        private Connection recordingConnection(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, arguments) -> {
                        Object result = invoke(method, connection, arguments);
                        if ("prepareStatement".equals(method.getName()) && arguments[0] instanceof String sql) {
                            return recordingPreparedStatement((PreparedStatement) result, sql);
                        }
                        return result;
                    }
            );
        }

        private PreparedStatement recordingPreparedStatement(PreparedStatement statement, String sql) {
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class},
                    (proxy, method, arguments) -> {
                        if ("executeQuery".equals(method.getName())) {
                            executedSql.add(sql);
                        }
                        return invoke(method, statement, arguments);
                    }
            );
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

    public static class SqlStatementInspector implements StatementInspector {

        private static final List<String> EXECUTED_SQL = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            EXECUTED_SQL.add(sql);
            return sql;
        }

        private static void clear() {
            EXECUTED_SQL.clear();
        }

        private static List<String> executedSql() {
            return List.copyOf(EXECUTED_SQL);
        }
    }
}
