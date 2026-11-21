-- =============================================================================
-- Idempotency-Key store (SYSTEM_DESIGN.md §12.1). One row per (caller, key):
-- reserved as IN_PROGRESS when the first request starts, COMPLETED with the
-- stored response (status, Content-Type, Location, body) once it succeeds, and
-- replayed for 24 h. An IN_PROGRESS row older than a 5-minute lease counts as
-- abandoned. Same portable subset
-- as V1 (D-10); ${timestamp_type} is set by MigrationPlaceholders.
-- =============================================================================
CREATE TABLE idempotency_keys (
    id                      BIGINT            NOT NULL,
    scope                   VARCHAR(64)       NOT NULL,
    idempotency_key         VARCHAR(64)       NOT NULL,
    request_hash            CHAR(64)          NOT NULL,
    state                   VARCHAR(16)       NOT NULL,
    response_status         INTEGER,
    response_content_type   VARCHAR(255),
    response_location       VARCHAR(2048),
    response_body           TEXT,
    expires_at              ${timestamp_type} NOT NULL,
    created_at              ${timestamp_type} NOT NULL,
    updated_at              ${timestamp_type} NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_keys_scope_key UNIQUE (scope, idempotency_key)
);
CREATE INDEX ix_idempotency_keys_expires ON idempotency_keys (expires_at);
