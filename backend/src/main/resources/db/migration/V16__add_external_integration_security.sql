CREATE TABLE external_integration_request_replays (
    id BIGSERIAL PRIMARY KEY,
    source VARCHAR(40) NOT NULL,
    request_id UUID NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_external_replay_source_request UNIQUE (source, request_id)
);

CREATE INDEX idx_external_replay_expiry
    ON external_integration_request_replays (expires_at, id);
