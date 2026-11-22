-- =============================================================================
-- Green-tick state on reviews (schema.md §7, D-134), the review part of the
-- design-v4 verification state. All columns nullable: SQLite cannot add a
-- NOT NULL column without a default, and null reads as UNVERIFIED. No backfill.
-- Inline REFERENCES in ADD COLUMN is enforced by SQLite and PostgreSQL; MySQL
-- ignores it, so db/vendor/mysql/V7_1__review_verification_fks.sql adds the FK.
-- Same portable subset as V1 (D-10); ${timestamp_type} is set by MigrationPlaceholders.
-- =============================================================================
ALTER TABLE reviews ADD COLUMN verification_status VARCHAR(16);
ALTER TABLE reviews ADD COLUMN verification_decided_by BIGINT REFERENCES users (id);
ALTER TABLE reviews ADD COLUMN verification_decided_at ${timestamp_type};
ALTER TABLE reviews ADD COLUMN verification_reason VARCHAR(1000);

CREATE INDEX ix_reviews_verification ON reviews (verification_status, created_at);
