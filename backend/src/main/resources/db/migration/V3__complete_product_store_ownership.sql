DO $$
BEGIN
    IF to_regclass('products') IS NULL THEN
        RETURN;
    END IF;

    IF EXISTS (SELECT 1 FROM products WHERE store_id IS NULL) THEN
        RAISE EXCEPTION 'Cannot migrate products: store_id contains null values';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM products p
        LEFT JOIN stores s ON s.id = p.store_id
        WHERE s.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot migrate products: store_id contains orphaned values';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_products_store') THEN
        ALTER TABLE products
            ADD CONSTRAINT fk_products_store
            FOREIGN KEY (store_id) REFERENCES stores (id);
    END IF;

    ALTER TABLE products ALTER COLUMN store_id SET NOT NULL;
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'products'
          AND column_name = 'status'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_products_store_status_id ON products (store_id, status, id);
    END IF;

    IF to_regclass('orders') IS NOT NULL THEN
        UPDATE orders o
        SET seller_id = s.owner_member_id
        FROM products p
        JOIN stores s ON s.id = p.store_id
        WHERE o.product_id = p.id
          AND o.seller_id IS NULL;

        IF EXISTS (SELECT 1 FROM orders WHERE seller_id IS NULL) THEN
            RAISE EXCEPTION 'Cannot migrate orders: seller_id contains null values';
        END IF;

        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_orders_seller') THEN
            ALTER TABLE orders
                ADD CONSTRAINT fk_orders_seller
                FOREIGN KEY (seller_id) REFERENCES members (id);
        END IF;

        ALTER TABLE orders ALTER COLUMN seller_id SET NOT NULL;
        CREATE INDEX IF NOT EXISTS idx_orders_seller_id ON orders (seller_id);
    END IF;

    ALTER TABLE products DROP COLUMN IF EXISTS seller_id;
END $$;
