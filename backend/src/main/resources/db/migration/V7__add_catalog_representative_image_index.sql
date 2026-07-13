CREATE INDEX IF NOT EXISTS idx_product_images_product_representative_sort_order_id
    ON product_images (product_id, representative DESC, sort_order ASC, id ASC);
