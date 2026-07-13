CREATE EXTENSION IF NOT EXISTS pg_trgm;

DO $$
BEGIN
    IF to_regclass('products') IS NULL THEN
        RETURN;
    END IF;

    ALTER TABLE products ADD COLUMN IF NOT EXISTS category VARCHAR(30);
    UPDATE products SET category = 'OTHER' WHERE category IS NULL;
    ALTER TABLE products ALTER COLUMN category SET NOT NULL;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_products_category') THEN
        ALTER TABLE products
            ADD CONSTRAINT chk_products_category
            CHECK (category IN ('COMPUTERS', 'MOBILE', 'HOME_APPLIANCES', 'VEHICLES', 'LIVING_HOBBY', 'OTHER'));
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'title'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_products_title_trgm
            ON products USING GIN (title gin_trgm_ops);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'description'
    ) THEN
        CREATE INDEX IF NOT EXISTS idx_products_description_trgm
            ON products USING GIN (description gin_trgm_ops);
    END IF;
END $$;
