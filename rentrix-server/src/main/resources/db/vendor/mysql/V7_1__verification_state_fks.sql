-- =============================================================================
-- MySQL only (D-10): MySQL parses but ignores the column-level REFERENCES of
-- V7's ALTER TABLE ... ADD COLUMN, so the two verification_decided_by FKs are
-- added here. Loaded from classpath:db/vendor/{vendor} (spring.flyway.locations),
-- so SQLite and PostgreSQL, which enforce the inline REFERENCES, never see it.
-- =============================================================================
ALTER TABLE tenancies ADD CONSTRAINT fk_tenancies_verification_decided_by FOREIGN KEY (verification_decided_by) REFERENCES users (id);
ALTER TABLE reviews   ADD CONSTRAINT fk_reviews_verification_decided_by   FOREIGN KEY (verification_decided_by) REFERENCES users (id);
