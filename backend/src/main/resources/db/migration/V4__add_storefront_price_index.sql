DO $$
BEGIN
    IF to_regclass('public.products') IS NOT NULL
       AND EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'store_id'
       )
       AND EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'status'
       )
       AND EXISTS (
           SELECT 1 FROM information_schema.columns
           WHERE table_schema = 'public' AND table_name = 'products' AND column_name = 'price'
       ) THEN
        CREATE INDEX IF NOT EXISTS idx_products_store_status_price_id
            ON products (store_id, status, price, id);
    END IF;
END $$;
