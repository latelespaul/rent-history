-- =============================================================================
-- Pincode rule from data (SYSTEM_DESIGN.md §5.2, D-84, D-98). A property's
-- 6-digit pincode must start with one of its city's comma-separated prefixes;
-- a city with no prefixes accepts any valid pincode. This replaces the
-- hard-coded 56xxxx / 41xxxx rule, so a new city needs only its row.
-- Same portable subset as V1 (D-10).
-- =============================================================================
ALTER TABLE cities ADD COLUMN pincode_prefixes VARCHAR(64);

UPDATE cities SET pincode_prefixes = '56' WHERE code = 'BLR';
UPDATE cities SET pincode_prefixes = '41' WHERE code = 'PNQ';
