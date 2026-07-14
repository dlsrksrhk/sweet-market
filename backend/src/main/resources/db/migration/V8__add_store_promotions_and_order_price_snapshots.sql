CREATE TABLE promotion_campaigns (
    id BIGSERIAL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    store_id BIGINT NOT NULL,
    scope VARCHAR(30) NOT NULL,
    discount_type VARCHAR(20) NOT NULL,
    discount_value BIGINT NOT NULL,
    priority INTEGER NOT NULL,
    title VARCHAR(100) NOT NULL,
    label VARCHAR(200),
    start_at TIMESTAMP WITH TIME ZONE NOT NULL,
    end_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lifecycle_status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_promotion_campaigns_scope CHECK (scope IN ('SELECTED_PRODUCTS', 'STORE_WIDE')),
    CONSTRAINT chk_promotion_campaigns_discount_type CHECK (discount_type IN ('FIXED_AMOUNT', 'PERCENTAGE')),
    CONSTRAINT chk_promotion_campaigns_discount_value CHECK (discount_value >= 0),
    CONSTRAINT chk_promotion_campaigns_period CHECK (start_at < end_at),
    CONSTRAINT chk_promotion_campaigns_lifecycle_status CHECK (lifecycle_status IN ('DRAFT', 'SCHEDULED', 'PAUSED', 'ENDED')),
    CONSTRAINT fk_promotion_campaigns_store FOREIGN KEY (store_id) REFERENCES stores (id)
);

CREATE TABLE promotion_targets (
    id BIGSERIAL PRIMARY KEY,
    promotion_campaign_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    CONSTRAINT uq_promotion_targets_campaign_product UNIQUE (promotion_campaign_id, product_id),
    CONSTRAINT fk_promotion_targets_campaign FOREIGN KEY (promotion_campaign_id) REFERENCES promotion_campaigns (id),
    CONSTRAINT fk_promotion_targets_product FOREIGN KEY (product_id) REFERENCES products (id)
);

CREATE INDEX idx_promotion_campaigns_store_lifecycle_period
    ON promotion_campaigns (store_id, lifecycle_status, start_at, end_at);
CREATE INDEX idx_promotion_targets_product_campaign
    ON promotion_targets (product_id, promotion_campaign_id);

DO $$
BEGIN
    IF to_regclass('public.orders') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE orders ADD COLUMN list_price BIGINT;
    ALTER TABLE orders ADD COLUMN promotion_campaign_id BIGINT;
    ALTER TABLE orders ADD COLUMN promotion_discount_amount BIGINT;
    ALTER TABLE orders ADD COLUMN final_price BIGINT;

    UPDATE orders o
    SET list_price = p.price,
        promotion_discount_amount = 0,
        final_price = p.price
    FROM products p
    WHERE o.product_id = p.id;

    ALTER TABLE orders ALTER COLUMN list_price SET NOT NULL;
    ALTER TABLE orders ALTER COLUMN promotion_discount_amount SET NOT NULL;
    ALTER TABLE orders ALTER COLUMN final_price SET NOT NULL;
    ALTER TABLE orders
        ADD CONSTRAINT fk_orders_promotion_campaign
        FOREIGN KEY (promotion_campaign_id) REFERENCES promotion_campaigns (id);
END $$;
