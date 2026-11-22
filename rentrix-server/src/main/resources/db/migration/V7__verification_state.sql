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

-- D-135: the headline aggregates count only green-tick reviews. No review is
-- VERIFIED yet, so recomputing every stored row from source gives zero counts:
-- this is that one-time recompute, run exactly once with the migration. The
-- nightly repair (D-33) and every review change rebuild rows from source as before.
UPDATE rating_aggregates
   SET review_count = 0, rating_sum = 0, average = NULL, bayes_score = NULL,
       count_1 = 0, count_2 = 0, count_3 = 0, count_4 = 0, count_5 = 0,
       sub_a_avg = NULL, sub_b_avg = NULL, sub_c_avg = NULL, last_review_at = NULL;
