package com.sweet.market.discovery.query;

import com.sweet.market.catalog.query.CatalogProductRow;
import com.sweet.market.discovery.api.ActiveEventResponse;
import com.sweet.market.discovery.api.EventDetailResponse;
import com.sweet.market.discovery.domain.DiscoveryEventType;
import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.store.domain.StoreType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class DiscoveryRepository {

    private static final String VISIBLE_PRODUCT_SQL = """
            p.status = 'ON_SALE'
              AND s.status = 'ACTIVE'
              AND (p.sales_policy = 'SINGLE_ITEM' OR i.total_quantity - i.reserved_quantity > 0)
            """;

    private static final String POPULAR_PRODUCTS_SQL = """
            WITH wishlist_scores AS (
                SELECT product_id, COUNT(*)::bigint AS wishlist_count
                FROM wishlist_items
                WHERE created_at >= :since
                GROUP BY product_id
            ), view_scores AS (
                SELECT product_id, COUNT(*)::bigint AS view_count
                FROM product_view_events
                WHERE viewed_at >= :since
                GROUP BY product_id
            )
            SELECT p.id AS product_id, p.title, p.price, p.price AS list_price, p.category,
                   representative_image.image_url AS representative_image_url, p.sales_policy,
                   i.total_quantity - i.reserved_quantity AS available_quantity, p.low_stock_threshold,
                   s.id AS store_id, s.owner_member_id AS seller_id, s.public_name AS store_name, s.type AS store_type,
                   promotion.promotion_id, promotion.promotion_title,
                   COALESCE(promotion.promotion_discount_amount, 0) AS promotion_discount_amount,
                   COALESCE(promotion.effective_price, p.price) AS effective_price
            FROM products p
            JOIN stores s ON s.id = p.store_id
            LEFT JOIN inventories i ON i.product_id = p.id
            JOIN LATERAL (
                SELECT pi.image_url
                FROM product_images pi
                WHERE pi.product_id = p.id AND pi.representative = true
                ORDER BY pi.sort_order ASC, pi.id ASC
                LIMIT 1
            ) representative_image ON TRUE
            LEFT JOIN wishlist_scores ws ON ws.product_id = p.id
            LEFT JOIN view_scores vs ON vs.product_id = p.id
            LEFT JOIN LATERAL (
                SELECT candidate.promotion_id, candidate.promotion_title, candidate.promotion_discount_amount,
                       p.price - candidate.promotion_discount_amount AS effective_price
                FROM (
                    SELECT pc.id AS promotion_id, pc.title AS promotion_title, pc.priority,
                           CASE
                               WHEN pc.discount_type = 'FIXED_AMOUNT' THEN LEAST(pc.discount_value, p.price)
                               WHEN pc.discount_value >= 100 THEN p.price
                               ELSE (p.price / 100) * pc.discount_value + ((p.price % 100) * pc.discount_value) / 100
                           END AS promotion_discount_amount
                    FROM promotion_campaigns pc
                    WHERE pc.store_id = p.store_id AND s.type = 'BUSINESS'
                      AND pc.lifecycle_status = 'SCHEDULED'
                      AND pc.start_at <= :now AND pc.end_at > :now
                      AND (pc.scope = 'STORE_WIDE' OR EXISTS (
                          SELECT 1 FROM promotion_targets pt
                          WHERE pt.promotion_campaign_id = pc.id AND pt.product_id = p.id
                      ))
                ) candidate
                ORDER BY p.price - candidate.promotion_discount_amount ASC, candidate.priority DESC, candidate.promotion_id ASC
                LIMIT 1
            ) promotion ON TRUE
            WHERE """ + " " + VISIBLE_PRODUCT_SQL + """
              AND COALESCE(ws.wishlist_count, 0) + COALESCE(vs.view_count, 0) > 0
            ORDER BY COALESCE(ws.wishlist_count, 0) * 5 + COALESCE(vs.view_count, 0) DESC, p.id DESC
            LIMIT 8
            """;

    private static final String EVENTS_SQL = """
            SELECT 'PROMOTION' AS event_type, pc.id AS event_id, pc.title, pc.label, s.id AS store_id,
                   s.public_name AS store_name, product_image.image_url AS representative_image_url, pc.end_at AS ends_at
            FROM promotion_campaigns pc
            JOIN stores s ON s.id = pc.store_id AND s.status = 'ACTIVE'
            JOIN LATERAL (
                SELECT pi.image_url
                FROM products p
                LEFT JOIN inventories i ON i.product_id = p.id
                JOIN product_images pi ON pi.product_id = p.id AND pi.representative = true
                WHERE p.store_id = pc.store_id AND """ + " " + VISIBLE_PRODUCT_SQL + """
                  AND (pc.scope = 'STORE_WIDE' OR EXISTS (
                      SELECT 1 FROM promotion_targets pt
                      WHERE pt.promotion_campaign_id = pc.id AND pt.product_id = p.id
                  ))
                ORDER BY p.id DESC, pi.sort_order ASC, pi.id ASC
                LIMIT 1
            ) product_image ON TRUE
            WHERE pc.lifecycle_status = 'SCHEDULED'
              AND pc.start_at <= :now AND pc.end_at > :now
            UNION ALL
            SELECT 'COUPON' AS event_type, cc.id AS event_id, cc.title, cc.label, s.id AS store_id,
                   s.public_name AS store_name, product_image.image_url AS representative_image_url, cc.issue_ends_at AS ends_at
            FROM coupon_campaigns cc
            LEFT JOIN stores s ON s.id = cc.store_id AND s.status = 'ACTIVE'
            JOIN LATERAL (
                SELECT pi.image_url
                FROM products p
                JOIN stores product_store ON product_store.id = p.store_id AND product_store.status = 'ACTIVE'
                LEFT JOIN inventories i ON i.product_id = p.id
                JOIN product_images pi ON pi.product_id = p.id AND pi.representative = true
                WHERE """ + " " + VISIBLE_PRODUCT_SQL.replace("s.status", "product_store.status") + """
                  AND (cc.owner_type = 'PLATFORM' OR p.store_id = cc.store_id)
                  AND (cc.scope = 'ALL_PRODUCTS' OR EXISTS (
                      SELECT 1 FROM coupon_campaign_targets ct
                      WHERE ct.coupon_campaign_id = cc.id AND ct.product_id = p.id
                  ))
                ORDER BY p.id DESC, pi.sort_order ASC, pi.id ASC
                LIMIT 1
            ) product_image ON TRUE
            WHERE cc.lifecycle_status = 'SCHEDULED'
              AND cc.issue_starts_at <= :now AND cc.issue_ends_at > :now
              AND (cc.owner_type = 'PLATFORM' OR s.id IS NOT NULL)
            """;

    private static final String EVENT_PRODUCTS_SQL = """
            SELECT p.id AS product_id, p.title, p.price, p.price AS list_price, p.category,
                   representative_image.image_url AS representative_image_url, p.sales_policy,
                   i.total_quantity - i.reserved_quantity AS available_quantity, p.low_stock_threshold,
                   s.id AS store_id, s.owner_member_id AS seller_id, s.public_name AS store_name, s.type AS store_type,
                   promotion.promotion_id, promotion.promotion_title,
                   COALESCE(promotion.promotion_discount_amount, 0) AS promotion_discount_amount,
                   COALESCE(promotion.effective_price, p.price) AS effective_price
            FROM products p
            JOIN stores s ON s.id = p.store_id
            LEFT JOIN inventories i ON i.product_id = p.id
            JOIN LATERAL (
                SELECT pi.image_url
                FROM product_images pi
                WHERE pi.product_id = p.id AND pi.representative = true
                ORDER BY pi.sort_order ASC, pi.id ASC
                LIMIT 1
            ) representative_image ON TRUE
            LEFT JOIN LATERAL (
                SELECT candidate.promotion_id, candidate.promotion_title, candidate.promotion_discount_amount,
                       p.price - candidate.promotion_discount_amount AS effective_price
                FROM (
                    SELECT pc.id AS promotion_id, pc.title AS promotion_title, pc.priority,
                           CASE
                               WHEN pc.discount_type = 'FIXED_AMOUNT' THEN LEAST(pc.discount_value, p.price)
                               WHEN pc.discount_value >= 100 THEN p.price
                               ELSE (p.price / 100) * pc.discount_value + ((p.price % 100) * pc.discount_value) / 100
                           END AS promotion_discount_amount
                    FROM promotion_campaigns pc
                    WHERE pc.store_id = p.store_id AND s.type = 'BUSINESS'
                      AND pc.lifecycle_status = 'SCHEDULED'
                      AND pc.start_at <= :now AND pc.end_at > :now
                      AND (pc.scope = 'STORE_WIDE' OR EXISTS (
                          SELECT 1 FROM promotion_targets pt
                          WHERE pt.promotion_campaign_id = pc.id AND pt.product_id = p.id
                      ))
                ) candidate
                ORDER BY p.price - candidate.promotion_discount_amount ASC, candidate.priority DESC, candidate.promotion_id ASC
                LIMIT 1
            ) promotion ON TRUE
            WHERE """ + " " + VISIBLE_PRODUCT_SQL + """
              AND (
                  (:eventType = 'PROMOTION' AND EXISTS (
                      SELECT 1
                      FROM promotion_campaigns pc
                      WHERE pc.id = :eventId
                        AND pc.store_id = p.store_id
                        AND pc.lifecycle_status = 'SCHEDULED'
                        AND pc.start_at <= :now AND pc.end_at > :now
                        AND (pc.scope = 'STORE_WIDE' OR EXISTS (
                            SELECT 1 FROM promotion_targets pt
                            WHERE pt.promotion_campaign_id = pc.id AND pt.product_id = p.id
                        ))
                  ))
                  OR (:eventType = 'COUPON' AND EXISTS (
                      SELECT 1
                      FROM coupon_campaigns cc
                      WHERE cc.id = :eventId
                        AND cc.lifecycle_status = 'SCHEDULED'
                        AND cc.issue_starts_at <= :now AND cc.issue_ends_at > :now
                        AND (cc.owner_type = 'PLATFORM' OR cc.store_id = p.store_id)
                        AND (cc.scope = 'ALL_PRODUCTS' OR EXISTS (
                            SELECT 1 FROM coupon_campaign_targets ct
                            WHERE ct.coupon_campaign_id = cc.id AND ct.product_id = p.id
                        ))
                  ))
              )
            ORDER BY p.id DESC
            LIMIT 8
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Clock clock;

    public DiscoveryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, Clock.systemUTC());
    }

    @Autowired
    public DiscoveryRepository(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Qualifier("discoveryClock") Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    public List<CatalogProductRow> findPopularProducts(OffsetDateTime since) {
        MapSqlParameterSource parameters = nowParameters().addValue("since", since);
        return jdbcTemplate.query(POPULAR_PRODUCTS_SQL, parameters, this::catalogProductRow);
    }

    public List<ActiveEventResponse> findActiveEvents() {
        return jdbcTemplate.query("SELECT * FROM (" + EVENTS_SQL + ") events "
                        + "ORDER BY ends_at ASC, CASE event_type WHEN 'PROMOTION' THEN 0 ELSE 1 END ASC, event_id ASC",
                nowParameters(), this::activeEventResponse);
    }

    public Optional<EventDetailResponse> findEvent(DiscoveryEventType eventType, Long eventId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventType", eventType.name())
                .addValue("eventId", eventId)
                .addValue("now", now());
        return jdbcTemplate.query("SELECT * FROM (" + EVENTS_SQL + ") events WHERE event_type = :eventType AND event_id = :eventId", parameters,
                (resultSet, rowNumber) -> eventDetailResponse(resultSet)).stream().findFirst();
    }

    public List<CatalogProductRow> findEventProducts(DiscoveryEventType eventType, Long eventId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("eventType", eventType.name())
                .addValue("eventId", eventId)
                .addValue("now", now());
        return jdbcTemplate.query(EVENT_PRODUCTS_SQL, parameters, this::catalogProductRow);
    }

    private MapSqlParameterSource nowParameters() {
        return new MapSqlParameterSource("now", now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(Instant.now(clock), ZoneOffset.UTC);
    }

    private CatalogProductRow catalogProductRow(ResultSet resultSet, int rowNumber) throws SQLException {
        ProductSalesPolicy salesPolicy = ProductSalesPolicy.valueOf(resultSet.getString("sales_policy"));
        return new CatalogProductRow(
                resultSet.getLong("product_id"), resultSet.getString("title"), resultSet.getLong("price"),
                resultSet.getLong("list_price"), nullableLong(resultSet, "promotion_id"), resultSet.getString("promotion_title"),
                resultSet.getLong("promotion_discount_amount"), resultSet.getLong("effective_price"),
                ProductCategory.valueOf(resultSet.getString("category")), resultSet.getString("representative_image_url"),
                new BuyerAvailabilityResponse(salesPolicy, ProductStatus.ON_SALE, nullableInteger(resultSet, "available_quantity"),
                        nullableInteger(resultSet, "low_stock_threshold")),
                salesPolicy, resultSet.getLong("store_id"), resultSet.getLong("seller_id"), resultSet.getString("store_name"),
                StoreType.valueOf(resultSet.getString("store_type"))
        );
    }

    private ActiveEventResponse activeEventResponse(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ActiveEventResponse(DiscoveryEventType.valueOf(resultSet.getString("event_type")), resultSet.getLong("event_id"),
                resultSet.getString("title"), resultSet.getString("label"), nullableLong(resultSet, "store_id"),
                resultSet.getString("store_name"), resultSet.getString("representative_image_url"),
                resultSet.getObject("ends_at", OffsetDateTime.class).toInstant());
    }

    private EventDetailResponse eventDetailResponse(ResultSet resultSet) throws SQLException {
        return new EventDetailResponse(DiscoveryEventType.valueOf(resultSet.getString("event_type")), resultSet.getLong("event_id"),
                resultSet.getString("title"), resultSet.getString("label"), nullableLong(resultSet, "store_id"),
                resultSet.getString("store_name"), resultSet.getString("representative_image_url"),
                resultSet.getObject("ends_at", OffsetDateTime.class).toInstant(), List.of());
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }
}
