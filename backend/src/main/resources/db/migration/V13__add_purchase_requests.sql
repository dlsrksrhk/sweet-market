CREATE TABLE purchase_requests (
    id BIGSERIAL PRIMARY KEY,
    buyer_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    execution_token UUID NOT NULL,
    lease_expires_at TIMESTAMPTZ NOT NULL,
    response_status INTEGER,
    response_payload JSONB,
    completed_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_purchase_requests_buyer_key UNIQUE (buyer_id, idempotency_key),
    CONSTRAINT chk_purchase_requests_status CHECK (status IN ('PROCESSING', 'COMPLETED'))
);
CREATE INDEX idx_purchase_requests_expiry ON purchase_requests (expires_at);
CREATE INDEX idx_purchase_requests_processing_lease
    ON purchase_requests (lease_expires_at) WHERE status = 'PROCESSING';

DO
$$
BEGIN
    IF to_regclass('public.members') IS NOT NULL
       AND NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_purchase_requests_buyer') THEN
        ALTER TABLE purchase_requests
            ADD CONSTRAINT fk_purchase_requests_buyer
                FOREIGN KEY (buyer_id) REFERENCES members (id);
    END IF;
END $$;
