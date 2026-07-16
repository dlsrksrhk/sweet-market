DO
$$
BEGIN
    IF
to_regclass('products') IS NULL THEN
        RETURN;
END IF;

ALTER TABLE products
    ADD COLUMN IF NOT EXISTS sales_policy VARCHAR (20);
ALTER TABLE products
    ADD COLUMN IF NOT EXISTS low_stock_threshold INTEGER;
UPDATE products
SET sales_policy = 'SINGLE_ITEM'
WHERE sales_policy IS NULL;
ALTER TABLE products
    ALTER COLUMN sales_policy SET NOT NULL;

IF
NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_sales_policy') THEN
ALTER TABLE products
    ADD CONSTRAINT chk_products_sales_policy
        CHECK (sales_policy IN ('SINGLE_ITEM', 'STOCK_MANAGED'));
END IF;

    IF
NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_low_stock_threshold') THEN
ALTER TABLE products
    ADD CONSTRAINT chk_products_low_stock_threshold
        CHECK (
            (sales_policy = 'SINGLE_ITEM' AND low_stock_threshold IS NULL)
                OR (sales_policy = 'STOCK_MANAGED' AND low_stock_threshold > 0)
            );
END IF;

CREATE TABLE IF NOT EXISTS inventories
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    version
    BIGINT
    NOT
    NULL
    DEFAULT
    0,
    product_id
    BIGINT
    NOT
    NULL
    UNIQUE,
    total_quantity
    INTEGER
    NOT
    NULL,
    reserved_quantity
    INTEGER
    NOT
    NULL
    DEFAULT
    0,
    CONSTRAINT
    chk_inventories_total_quantity_non_negative
    CHECK
(
    total_quantity
    >=
    0
),
    CONSTRAINT chk_inventories_reserved_quantity_non_negative CHECK
(
    reserved_quantity
    >=
    0
),
    CONSTRAINT chk_inventories_reserved_quantity_within_total CHECK
(
    reserved_quantity
    <=
    total_quantity
)
    );

CREATE TABLE IF NOT EXISTS inventory_adjustments
(
    id
    BIGSERIAL
    PRIMARY
    KEY,
    inventory_id
    BIGINT
    NOT
    NULL,
    product_id
    BIGINT
    NOT
    NULL,
    order_id
    BIGINT,
    actor_member_id
    BIGINT,
    change_type
    VARCHAR
(
    30
) NOT NULL,
    reason VARCHAR
(
    30
),
    reference_note VARCHAR
(
    500
),
    before_total_quantity INTEGER NOT NULL,
    after_total_quantity INTEGER NOT NULL,
    before_reserved_quantity INTEGER NOT NULL,
    after_reserved_quantity INTEGER NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    );

CREATE INDEX IF NOT EXISTS idx_inventory_adjustments_product_occurred_at
    ON inventory_adjustments (product_id, occurred_at DESC, id DESC);

IF
NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inventories_product') THEN
ALTER TABLE inventories
    ADD CONSTRAINT fk_inventories_product
        FOREIGN KEY (product_id) REFERENCES products (id);
END IF;

    IF
NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inventory_adjustments_inventory') THEN
ALTER TABLE inventory_adjustments
    ADD CONSTRAINT fk_inventory_adjustments_inventory
        FOREIGN KEY (inventory_id) REFERENCES inventories (id);
END IF;

    IF
to_regclass('products') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inventory_adjustments_product') THEN
ALTER TABLE inventory_adjustments
    ADD CONSTRAINT fk_inventory_adjustments_product
        FOREIGN KEY (product_id) REFERENCES products (id);
END IF;

    IF
to_regclass('orders') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inventory_adjustments_order') THEN
ALTER TABLE inventory_adjustments
    ADD CONSTRAINT fk_inventory_adjustments_order
        FOREIGN KEY (order_id) REFERENCES orders (id);
END IF;

    IF
to_regclass('members') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inventory_adjustments_actor') THEN
ALTER TABLE inventory_adjustments
    ADD CONSTRAINT fk_inventory_adjustments_actor
        FOREIGN KEY (actor_member_id) REFERENCES members (id);
END IF;
END $$;
