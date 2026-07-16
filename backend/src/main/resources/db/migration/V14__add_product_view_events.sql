CREATE TABLE product_view_events (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products(id),
    visitor_hash CHAR(64) NOT NULL,
    viewed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_product_view_events_product_viewed_at
    ON product_view_events (product_id, viewed_at DESC);

CREATE TABLE product_view_deduplications (
    product_id BIGINT NOT NULL REFERENCES products(id),
    visitor_hash CHAR(64) NOT NULL,
    last_counted_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (product_id, visitor_hash)
);
