package com.sweet.market.catalog.query;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.sweet.market.catalog.domain.CatalogAvailabilityFilter;
import com.sweet.market.catalog.domain.CatalogSort;
import com.sweet.market.inventory.api.BuyerAvailabilityResponse;
import com.sweet.market.product.domain.ProductCategory;
import com.sweet.market.product.domain.ProductSalesPolicy;
import com.sweet.market.product.domain.ProductStatus;
import com.sweet.market.store.domain.StoreType;

@Repository
public class CatalogSearchRepository {

    private static final String BASE_SQL = """
            SELECT p.id AS product_id,
                   p.title,
                   p.price,
                   p.price AS list_price,
                   p.category,
                   representative_image.image_url AS representative_image_url,
                   p.sales_policy,
                   i.total_quantity - i.reserved_quantity AS available_quantity,
                   p.low_stock_threshold,
                   s.id AS store_id,
                   s.owner_member_id AS seller_id,
                   s.public_name AS store_name,
                   s.type AS store_type,
                   promotion.promotion_id,
                   promotion.promotion_title,
                   COALESCE(promotion.promotion_discount_amount, 0) AS promotion_discount_amount,
                   COALESCE(promotion.effective_price, p.price) AS effective_price
            FROM products p
            JOIN stores s ON s.id = p.store_id AND s.status = 'ACTIVE'
            LEFT JOIN inventories i ON i.product_id = p.id
            LEFT JOIN LATERAL (
                SELECT image_url
                FROM product_images pi
                WHERE pi.product_id = p.id
                ORDER BY pi.representative DESC, pi.sort_order ASC, pi.id ASC
                LIMIT 1
            ) representative_image ON TRUE
            LEFT JOIN LATERAL (
                SELECT candidate.promotion_id,
                       candidate.promotion_title,
                       candidate.promotion_discount_amount,
                       p.price - candidate.promotion_discount_amount AS effective_price
                FROM (
                    SELECT pc.id AS promotion_id,
                           pc.title AS promotion_title,
                           pc.priority,
                           CASE
                               WHEN pc.discount_type = 'FIXED_AMOUNT' THEN LEAST(pc.discount_value, p.price)
                               WHEN pc.discount_value >= 100 THEN p.price
                               ELSE (p.price / 100) * pc.discount_value
                                       + ((p.price % 100) * pc.discount_value) / 100
                           END AS promotion_discount_amount
                    FROM promotion_campaigns pc
                    WHERE pc.store_id = p.store_id
                      AND s.type = 'BUSINESS'
                      AND pc.lifecycle_status IN ('DRAFT', 'SCHEDULED')
                      AND pc.start_at <= CURRENT_TIMESTAMP
                      AND pc.end_at > CURRENT_TIMESTAMP
                      AND (pc.scope = 'STORE_WIDE' OR EXISTS (
                          SELECT 1
                          FROM promotion_targets pt
                          WHERE pt.promotion_campaign_id = pc.id
                            AND pt.product_id = p.id
                      ))
                ) candidate
                ORDER BY p.price - candidate.promotion_discount_amount ASC,
                         candidate.priority DESC,
                         candidate.promotion_id ASC
                LIMIT 1
            ) promotion ON TRUE
            WHERE p.status = 'ON_SALE'
              AND (p.sales_policy = 'SINGLE_ITEM' OR i.total_quantity - i.reserved_quantity > 0)
            """;

    private static final String KEYWORD_MATCH_CTE = """
            WITH keyword_matches AS MATERIALIZED (
                SELECT id
                FROM products
                WHERE title ILIKE :keywordPattern
                UNION
                SELECT id
                FROM products
                WHERE description ILIKE :keywordPattern
            )
            """;

    private static final String KEYWORD_BASE_SQL = BASE_SQL.replace(
            "FROM products p",
            "FROM keyword_matches km JOIN products p ON p.id = km.id"
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CatalogSearchRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<CatalogProductRow> findPage(CatalogSearchCriteria criteria, CatalogCursor cursor) {
        boolean hasKeyword = criteria.keyword() != null && !criteria.keyword().isBlank();
        StringBuilder sql = new StringBuilder(hasKeyword ? KEYWORD_MATCH_CTE + KEYWORD_BASE_SQL : BASE_SQL);
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        appendFilters(sql, parameters, criteria);
        appendSeek(sql, parameters, criteria.sort(), cursor);
        sql.append(" ORDER BY ").append(orderBy(criteria.sort()));
        sql.append(" LIMIT :limitPlusOne");
        parameters.addValue("limitPlusOne", criteria.size() + 1);

        return jdbcTemplate.query(sql.toString(), parameters, catalogProductRowMapper());
    }

    private void appendFilters(StringBuilder sql, MapSqlParameterSource parameters, CatalogSearchCriteria criteria) {
        if (criteria.keyword() != null && !criteria.keyword().isBlank()) {
            parameters.addValue("keywordPattern", "%" + criteria.keyword() + "%");
        }
        if (criteria.category() != null) {
            sql.append(" AND p.category = :category");
            parameters.addValue("category", criteria.category().name());
        }
        if (criteria.minPrice() != null) {
            sql.append(" AND COALESCE(promotion.effective_price, p.price) >= :minPrice");
            parameters.addValue("minPrice", criteria.minPrice());
        }
        if (criteria.maxPrice() != null) {
            sql.append(" AND COALESCE(promotion.effective_price, p.price) <= :maxPrice");
            parameters.addValue("maxPrice", criteria.maxPrice());
        }
        if (criteria.availability() != null) {
            appendAvailability(sql, criteria.availability());
        }
        if (criteria.salesPolicy() != null) {
            sql.append(" AND p.sales_policy = :salesPolicy");
            parameters.addValue("salesPolicy", criteria.salesPolicy().name());
        }
        if (criteria.storeType() != null) {
            sql.append(" AND s.type = :storeType");
            parameters.addValue("storeType", criteria.storeType().name());
        }
        if (criteria.storeId() != null) {
            sql.append(" AND s.id = :storeId");
            parameters.addValue("storeId", criteria.storeId());
        }
    }

    private void appendAvailability(StringBuilder sql, CatalogAvailabilityFilter availability) {
        if (availability == CatalogAvailabilityFilter.IN_STOCK) {
            sql.append(" AND (p.sales_policy = 'SINGLE_ITEM' OR i.total_quantity - i.reserved_quantity > p.low_stock_threshold)");
            return;
        }
        sql.append(" AND p.sales_policy = 'STOCK_MANAGED'")
                .append(" AND i.total_quantity - i.reserved_quantity <= p.low_stock_threshold");
    }

    private void appendSeek(
            StringBuilder sql,
            MapSqlParameterSource parameters,
            CatalogSort sort,
            CatalogCursor cursor
    ) {
        if (cursor == null) {
            return;
        }
        if (cursor.sort() != sort) {
            throw new IllegalArgumentException("cursor sort must match criteria sort");
        }
        parameters.addValue("cursorId", cursor.productId());
        switch (sort) {
            case NEWEST -> sql.append(" AND p.id < :cursorId");
            case PRICE_ASC -> {
                parameters.addValue("cursorPrice", cursor.price());
                sql.append(" AND (COALESCE(promotion.effective_price, p.price) > :cursorPrice"
                        + " OR (COALESCE(promotion.effective_price, p.price) = :cursorPrice AND p.id > :cursorId))");
            }
            case PRICE_DESC -> {
                parameters.addValue("cursorPrice", cursor.price());
                sql.append(" AND (COALESCE(promotion.effective_price, p.price) < :cursorPrice"
                        + " OR (COALESCE(promotion.effective_price, p.price) = :cursorPrice AND p.id < :cursorId))");
            }
        }
    }

    private String orderBy(CatalogSort sort) {
        return switch (sort) {
            case NEWEST -> "p.id DESC";
            case PRICE_ASC -> "COALESCE(promotion.effective_price, p.price) ASC, p.id ASC";
            case PRICE_DESC -> "COALESCE(promotion.effective_price, p.price) DESC, p.id DESC";
        };
    }

    private RowMapper<CatalogProductRow> catalogProductRowMapper() {
        return (resultSet, rowNumber) -> new CatalogProductRow(
                resultSet.getLong("product_id"),
                resultSet.getString("title"),
                resultSet.getLong("price"),
                resultSet.getLong("list_price"),
                nullableLong(resultSet, "promotion_id"),
                resultSet.getString("promotion_title"),
                resultSet.getLong("promotion_discount_amount"),
                resultSet.getLong("effective_price"),
                ProductCategory.valueOf(resultSet.getString("category")),
                resultSet.getString("representative_image_url"),
                buyerAvailability(resultSet),
                ProductSalesPolicy.valueOf(resultSet.getString("sales_policy")),
                resultSet.getLong("store_id"),
                resultSet.getLong("seller_id"),
                resultSet.getString("store_name"),
                StoreType.valueOf(resultSet.getString("store_type"))
        );
    }

    private BuyerAvailabilityResponse buyerAvailability(ResultSet resultSet) throws SQLException {
        ProductSalesPolicy salesPolicy = ProductSalesPolicy.valueOf(resultSet.getString("sales_policy"));
        Integer availableQuantity = nullableInteger(resultSet, "available_quantity");
        Integer lowStockThreshold = nullableInteger(resultSet, "low_stock_threshold");
        return new BuyerAvailabilityResponse(salesPolicy, ProductStatus.ON_SALE, availableQuantity, lowStockThreshold);
    }

    private Integer nullableInteger(ResultSet resultSet, String columnName) throws SQLException {
        int value = resultSet.getInt(columnName);
        return resultSet.wasNull() ? null : value;
    }

    private Long nullableLong(ResultSet resultSet, String columnName) throws SQLException {
        long value = resultSet.getLong(columnName);
        return resultSet.wasNull() ? null : value;
    }
}
