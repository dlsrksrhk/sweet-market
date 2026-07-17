CREATE TABLE projection_generations (
    id BIGSERIAL PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    cutoff_at TIMESTAMPTZ NOT NULL,
    tracking_started_at TIMESTAMPTZ NOT NULL,
    bootstrap_high_water_id BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    activated_at TIMESTAMPTZ,
    retired_at TIMESTAMPTZ,
    CONSTRAINT chk_projection_generations_status
        CHECK (status IN ('BUILDING', 'ACTIVE', 'RETIRED', 'FAILED'))
);

CREATE UNIQUE INDEX uq_projection_generations_active
    ON projection_generations ((status)) WHERE status = 'ACTIVE';

CREATE TABLE operational_event_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    event_type VARCHAR(80) NOT NULL,
    schema_version INTEGER NOT NULL,
    aggregate_type VARCHAR(40) NOT NULL,
    aggregate_id BIGINT,
    aggregate_version BIGINT,
    store_id BIGINT,
    campaign_id BIGINT,
    partition_key VARCHAR(160) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    payload JSONB NOT NULL,
    delivery_state VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error VARCHAR(1000),
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_operational_event_outbox_state
        CHECK (delivery_state IN ('PENDING', 'RETRY', 'PROCESSED', 'DEAD')),
    CONSTRAINT chk_operational_event_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX idx_operational_event_outbox_poll
    ON operational_event_outbox (delivery_state, next_attempt_at, id);
CREATE INDEX idx_operational_event_outbox_cleanup
    ON operational_event_outbox (processed_at) WHERE delivery_state = 'PROCESSED';

CREATE TABLE projection_event_receipts (
    id BIGSERIAL PRIMARY KEY,
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    projection_name VARCHAR(80) NOT NULL,
    event_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_projection_event_receipts_generation_projection_event
        UNIQUE (generation_id, projection_name, event_id)
);

CREATE INDEX idx_projection_event_receipts_generation_processed_at
    ON projection_event_receipts (generation_id, processed_at DESC);

CREATE TABLE store_metric_hourly (
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    store_id BIGINT NOT NULL,
    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
    order_success_count BIGINT NOT NULL DEFAULT 0,
    purchase_failure_count BIGINT NOT NULL DEFAULT 0,
    reservation_failure_count BIGINT NOT NULL DEFAULT 0,
    promotion_applied_amount BIGINT NOT NULL DEFAULT 0,
    promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0,
    promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
    coupon_applied_amount BIGINT NOT NULL DEFAULT 0,
    coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0,
    coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
    sold_out_transition_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (generation_id, bucket_start, store_id, outcome_reason)
);

CREATE INDEX idx_store_metric_hourly_store_bucket
    ON store_metric_hourly (generation_id, store_id, bucket_start);

CREATE TABLE campaign_metric_hourly (
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    commerce_store_id BIGINT NOT NULL DEFAULT 0,
    campaign_kind VARCHAR(20) NOT NULL,
    campaign_id BIGINT NOT NULL,
    campaign_owner_type VARCHAR(20) NOT NULL,
    campaign_owner_store_id BIGINT NOT NULL DEFAULT 0,
    outcome_reason VARCHAR(60) NOT NULL DEFAULT 'NONE',
    claim_success_count BIGINT NOT NULL DEFAULT 0,
    claim_failure_count BIGINT NOT NULL DEFAULT 0,
    redemption_success_count BIGINT NOT NULL DEFAULT 0,
    redemption_failure_count BIGINT NOT NULL DEFAULT 0,
    order_success_count BIGINT NOT NULL DEFAULT 0,
    purchase_failure_count BIGINT NOT NULL DEFAULT 0,
    promotion_applied_amount BIGINT NOT NULL DEFAULT 0,
    promotion_realized_amount BIGINT NOT NULL DEFAULT 0,
    promotion_canceled_amount BIGINT NOT NULL DEFAULT 0,
    promotion_refunded_amount BIGINT NOT NULL DEFAULT 0,
    coupon_applied_amount BIGINT NOT NULL DEFAULT 0,
    coupon_realized_amount BIGINT NOT NULL DEFAULT 0,
    coupon_canceled_amount BIGINT NOT NULL DEFAULT 0,
    coupon_refunded_amount BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (
        generation_id, bucket_start, commerce_store_id, campaign_kind,
        campaign_id, campaign_owner_type, campaign_owner_store_id, outcome_reason
    )
);

CREATE INDEX idx_campaign_metric_hourly_store_bucket
    ON campaign_metric_hourly (generation_id, commerce_store_id, bucket_start, campaign_kind, campaign_id);

CREATE INDEX idx_campaign_metric_hourly_owner_store_bucket
    ON campaign_metric_hourly (
        generation_id, campaign_owner_store_id, bucket_start, campaign_kind, campaign_id
    );

CREATE TABLE inventory_pressure_projection (
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    sales_policy VARCHAR(20) NOT NULL,
    available_quantity INTEGER,
    low_stock BOOLEAN NOT NULL,
    last_sold_out_at TIMESTAMPTZ,
    recent_reservation_failure_count BIGINT NOT NULL DEFAULT 0,
    last_reservation_failure_at TIMESTAMPTZ,
    aggregate_version BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (generation_id, product_id)
);

CREATE INDEX idx_inventory_pressure_store_attention
    ON inventory_pressure_projection (
        generation_id, store_id, low_stock DESC,
        recent_reservation_failure_count DESC, product_id DESC
    );

CREATE TABLE inventory_failure_hourly (
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    bucket_start TIMESTAMPTZ NOT NULL,
    product_id BIGINT NOT NULL,
    store_id BIGINT NOT NULL,
    failure_count BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (generation_id, bucket_start, product_id)
);

CREATE INDEX idx_inventory_failure_hourly_store_bucket
    ON inventory_failure_hourly (generation_id, store_id, bucket_start, product_id);

CREATE TABLE campaign_audit_projection (
    id BIGSERIAL PRIMARY KEY,
    generation_id BIGINT NOT NULL REFERENCES projection_generations(id) ON DELETE CASCADE,
    event_id UUID NOT NULL,
    campaign_kind VARCHAR(20) NOT NULL,
    campaign_id BIGINT NOT NULL,
    owner_type VARCHAR(20) NOT NULL,
    owner_store_id BIGINT NOT NULL DEFAULT 0,
    actor_member_id BIGINT NOT NULL,
    command VARCHAR(20) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    aggregate_version BIGINT,
    before_summary JSONB,
    after_summary JSONB NOT NULL,
    CONSTRAINT uq_campaign_audit_projection_generation_event UNIQUE (generation_id, event_id)
);

CREATE INDEX idx_campaign_audit_projection_owner_time
    ON campaign_audit_projection (
        generation_id, owner_store_id, occurred_at DESC,
        aggregate_version DESC NULLS LAST, event_id DESC
    );

CREATE TABLE performance_measurement_runs (
    id BIGSERIAL PRIMARY KEY,
    measurement_id UUID NOT NULL UNIQUE,
    payload_hash VARCHAR(64) NOT NULL,
    git_commit VARCHAR(64) NOT NULL,
    dirty_worktree BOOLEAN NOT NULL,
    fixture_version VARCHAR(80) NOT NULL,
    scenario_version VARCHAR(80) NOT NULL,
    environment_name VARCHAR(80) NOT NULL,
    hardware_description VARCHAR(500) NOT NULL,
    artifact_directory VARCHAR(500) NOT NULL,
    warmup_seconds INTEGER NOT NULL,
    measured_seconds INTEGER NOT NULL,
    off_started_at TIMESTAMPTZ NOT NULL,
    off_completed_at TIMESTAMPTZ NOT NULL,
    on_started_at TIMESTAMPTZ NOT NULL,
    on_completed_at TIMESTAMPTZ NOT NULL,
    registered_by BIGINT NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_performance_measurement_duration CHECK (warmup_seconds > 0 AND measured_seconds > 0)
);

CREATE TABLE performance_endpoint_metrics (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES performance_measurement_runs(id) ON DELETE CASCADE,
    cache_mode VARCHAR(10) NOT NULL,
    endpoint VARCHAR(40) NOT NULL,
    p50_millis NUMERIC(12,3) NOT NULL,
    p95_millis NUMERIC(12,3) NOT NULL,
    throughput_per_second NUMERIC(14,3) NOT NULL,
    error_rate NUMERIC(8,7) NOT NULL,
    jdbc_statement_count BIGINT NOT NULL,
    cache_hit_count BIGINT,
    cache_miss_count BIGINT,
    cache_eviction_count BIGINT,
    CONSTRAINT uq_performance_endpoint_metrics_run_mode_endpoint UNIQUE (run_id, cache_mode, endpoint),
    CONSTRAINT chk_performance_endpoint_metrics_mode CHECK (cache_mode IN ('OFF', 'ON')),
    CONSTRAINT chk_performance_endpoint_metrics_percentiles CHECK (p50_millis >= 0 AND p95_millis >= p50_millis),
    CONSTRAINT chk_performance_endpoint_metrics_error_rate CHECK (error_rate >= 0 AND error_rate <= 1)
);

CREATE TABLE performance_query_evidence (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES performance_measurement_runs(id) ON DELETE CASCADE,
    cache_mode VARCHAR(10) NOT NULL,
    query_shape VARCHAR(40) NOT NULL,
    bind_summary VARCHAR(1000) NOT NULL,
    plan_summary VARCHAR(4000) NOT NULL,
    execution_millis NUMERIC(12,3) NOT NULL,
    actual_rows BIGINT NOT NULL,
    shared_hit_blocks BIGINT NOT NULL,
    shared_read_blocks BIGINT NOT NULL,
    CONSTRAINT uq_performance_query_evidence_run_mode_shape UNIQUE (run_id, cache_mode, query_shape),
    CONSTRAINT chk_performance_query_evidence_mode CHECK (cache_mode IN ('OFF', 'ON')),
    CONSTRAINT chk_performance_query_evidence_values CHECK (
        execution_millis >= 0 AND actual_rows >= 0 AND shared_hit_blocks >= 0 AND shared_read_blocks >= 0
    )
);
