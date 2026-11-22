-- =============================================================================
-- MySQL only (D-10): MySQL parses but ignores the column-level REFERENCES of
-- V9's ALTER TABLE ... ADD COLUMN, so the added_by FK is added here. Loaded from
-- classpath:db/vendor/{vendor} (spring.flyway.locations), so SQLite and
-- PostgreSQL, which enforce the inline REFERENCES, never see it.
-- =============================================================================
ALTER TABLE properties ADD CONSTRAINT fk_properties_added_by FOREIGN KEY (added_by) REFERENCES users (id);
