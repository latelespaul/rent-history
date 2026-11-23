-- =============================================================================
-- Owners edit their own entries (SYSTEM_DESIGN.md D-156, captain 2026-11-23
-- 21:30): the creator of a tenancy, a home, a rent record or a review may change
-- or delete it; an edit of a verified item takes its green tick away and puts it
-- back in the admin queue. Every change is kept here, field by field (before and
-- after), for admins only. Append-only: rows are never updated or deleted.
-- target_type is the item whose green tick the change touches (TENANCY,
-- PROPERTY, REVIEW; a rent-record change is kept under its TENANCY); edit_ref
-- groups the rows of one save. Values are stored masked like everywhere else
-- (no full ID numbers). (V13 is taken by the rental agreements PR.) Same
-- portable subset as V1 (D-10).
-- =============================================================================
CREATE TABLE edit_history (
    id              BIGINT            NOT NULL,
    edit_ref        CHAR(36)          NOT NULL,
    editor_id       BIGINT            NOT NULL,
    target_type     VARCHAR(16)       NOT NULL,
    target_id       BIGINT            NOT NULL,
    target_ref      VARCHAR(36)       NOT NULL,
    change_type     VARCHAR(16)       NOT NULL,
    field           VARCHAR(64)       NOT NULL,
    old_value       VARCHAR(2000),
    new_value       VARCHAR(2000),
    was_verified    BOOLEAN           NOT NULL DEFAULT FALSE,
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_edit_history PRIMARY KEY (id),
    CONSTRAINT fk_edit_history_editor FOREIGN KEY (editor_id) REFERENCES users (id)
);
CREATE INDEX ix_edit_history_target ON edit_history (target_type, target_ref, created_at);
