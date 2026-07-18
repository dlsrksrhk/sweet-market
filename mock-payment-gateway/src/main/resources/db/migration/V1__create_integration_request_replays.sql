CREATE TABLE integration_request_replays (
    id BIGSERIAL PRIMARY KEY,
    client_id VARCHAR(80) NOT NULL,
    request_id UUID NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_gateway_replay_client_request UNIQUE (client_id, request_id)
);

CREATE INDEX idx_gateway_replay_expiry
    ON integration_request_replays (expires_at, id);
