-- =============================================================================
-- MySQL only (D-10, D-114): MySQL parses but ignores the column-level REFERENCES
-- of V5's ALTER TABLE ... ADD COLUMN, so the four properties FKs are added here.
-- Loaded from classpath:db/vendor/{vendor} (spring.flyway.locations), so SQLite
-- and PostgreSQL, which enforce the inline REFERENCES, never see this version.
-- =============================================================================
ALTER TABLE properties ADD CONSTRAINT fk_properties_area    FOREIGN KEY (area_id)    REFERENCES areas (id);
ALTER TABLE properties ADD CONSTRAINT fk_properties_society FOREIGN KEY (society_id) REFERENCES societies (id);
ALTER TABLE properties ADD CONSTRAINT fk_properties_tower   FOREIGN KEY (tower_id)   REFERENCES society_towers (id);
ALTER TABLE properties ADD CONSTRAINT fk_properties_wing    FOREIGN KEY (wing_id)    REFERENCES society_wings (id);
