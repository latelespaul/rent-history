-- =============================================================================
-- Verification state, the green tick (SYSTEM_DESIGN.md D-132 – D-135, schema.md
-- §7; design v4, open by default). Every item is visible at once; an admin's
-- approval gives it the "Verified by Doorway Living" tick. Properties already
-- have review_reason, reviewed_by, reviewed_at (V6) and gain the state;
-- tenancies and reviews gain the state, who decided, when and why. All
-- columns are nullable (SQLite cannot add a NOT NULL column without a
-- default); null reads as UNVERIFIED. MySQL ignores the inline REFERENCES, so
-- db/vendor/mysql/V7_1__verification_state_fks.sql adds those two FKs.
-- Same portable subset as V1 (D-10); ${timestamp_type} is set by
-- MigrationPlaceholders.
-- =============================================================================
ALTER TABLE properties ADD COLUMN verification_status VARCHAR(16);

ALTER TABLE tenancies ADD COLUMN verification_status VARCHAR(16);
ALTER TABLE tenancies ADD COLUMN verification_decided_by BIGINT REFERENCES users (id);
ALTER TABLE tenancies ADD COLUMN verification_decided_at ${timestamp_type};
ALTER TABLE tenancies ADD COLUMN verification_reason VARCHAR(1000);

ALTER TABLE reviews ADD COLUMN verification_status VARCHAR(16);
ALTER TABLE reviews ADD COLUMN verification_decided_by BIGINT REFERENCES users (id);
ALTER TABLE reviews ADD COLUMN verification_decided_at ${timestamp_type};
ALTER TABLE reviews ADD COLUMN verification_reason VARCHAR(1000);

CREATE INDEX ix_properties_verification ON properties (verification_status, created_at);
CREATE INDEX ix_tenancies_verification ON tenancies (verification_status, created_at);
CREATE INDEX ix_reviews_verification ON reviews (verification_status, created_at);

