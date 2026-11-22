-- =============================================================================
-- Homes added by their tenant or owner (SYSTEM_DESIGN.md D-139, D-140; design
-- v4, open by default). A tenant can add the home they rent when its landlord is
-- not on the platform: who added it, the owner's name and contact as the tenant
-- gave them (private: only the person who entered them and admins), and one
-- visibility switch per sensitive field, so masking can be turned on later
-- without a migration. Existing rows keep today's rules: flat numbers stay
-- MASKED (D-57), rent stays OPEN to signed-in users (D-26, D-56). MySQL ignores
-- the inline REFERENCES, so db/vendor/mysql/V9_1__home_records_fks.sql adds it.
-- Same portable subset as V1 (D-10).
-- =============================================================================
ALTER TABLE properties ADD COLUMN added_by BIGINT REFERENCES users (id);
ALTER TABLE properties ADD COLUMN owner_name VARCHAR(80);
ALTER TABLE properties ADD COLUMN owner_email VARCHAR(320);
ALTER TABLE properties ADD COLUMN owner_phone VARCHAR(20);
ALTER TABLE properties ADD COLUMN unit_label_visibility VARCHAR(16) DEFAULT 'MASKED' NOT NULL;
ALTER TABLE properties ADD COLUMN owner_contact_visibility VARCHAR(16) DEFAULT 'PRIVATE' NOT NULL;

ALTER TABLE tenancies ADD COLUMN rent_visibility VARCHAR(16) DEFAULT 'OPEN' NOT NULL;

CREATE INDEX ix_properties_added_by ON properties (added_by);
