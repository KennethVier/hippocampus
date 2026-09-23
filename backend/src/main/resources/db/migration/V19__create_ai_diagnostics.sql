CREATE TABLE ai_request_records (
    id UUID NOT NULL,
    user_id UUID NULL,
    task_type VARCHAR NOT NULL,
    prompt_id VARCHAR NOT NULL,
    prompt_version VARCHAR NOT NULL,
    provider VARCHAR NOT NULL,
    model VARCHAR NOT NULL,
    status VARCHAR NOT NULL,
    grounding_mode VARCHAR NULL,
    input_token_count INT NULL,
    output_token_count INT NULL,
    latency_ms BIGINT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    error_code VARCHAR NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_ai_request_records PRIMARY KEY (id),
    CONSTRAINT fk_ai_request_records_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_ai_request_records_provider_created_at
    ON ai_request_records (provider, created_at);

CREATE TABLE provider_usage_records (
    id UUID NOT NULL,
    provider VARCHAR NOT NULL,
    model VARCHAR NOT NULL,
    user_id UUID NULL,
    task_type VARCHAR NOT NULL,
    request_count INT NOT NULL DEFAULT 1,
    input_tokens BIGINT NULL,
    output_tokens BIGINT NULL,
    estimated_cost NUMERIC NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_provider_usage_records PRIMARY KEY (id),
    CONSTRAINT fk_provider_usage_records_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);
