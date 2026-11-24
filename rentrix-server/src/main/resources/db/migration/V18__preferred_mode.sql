-- =============================================================================
-- The header's Tenant | Owner mode follows the account, not the browser
-- (PLAN R10). NULL = never chosen: the web falls back to its own default.
-- Values TENANT | OWNER are checked by the API; enum columns have no CHECK (V1).
-- Same portable subset as V1 (D-10).
-- =============================================================================
ALTER TABLE users ADD COLUMN preferred_mode VARCHAR(8);
