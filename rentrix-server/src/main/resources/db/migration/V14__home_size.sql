-- =============================================================================
-- Home size (captain 2026-11-23 17:18): 1 RK, 1-4 BHK, 5+ BHK, studio, or other
-- with free text, picked as chips when a home is added and offered as a search
-- filter. Existing homes get the size their bedrooms and type already say.
-- (V13 is taken by the rental agreements PR.) Same portable subset as V1 (D-10).
-- =============================================================================
ALTER TABLE properties ADD COLUMN home_size VARCHAR(16);
ALTER TABLE properties ADD COLUMN home_size_other VARCHAR(40);

UPDATE properties SET home_size = 'STUDIO' WHERE property_type = 'STUDIO';
UPDATE properties SET home_size = 'BHK1' WHERE home_size IS NULL AND bedrooms = 1;
UPDATE properties SET home_size = 'BHK2' WHERE home_size IS NULL AND bedrooms = 2;
UPDATE properties SET home_size = 'BHK3' WHERE home_size IS NULL AND bedrooms = 3;
UPDATE properties SET home_size = 'BHK4' WHERE home_size IS NULL AND bedrooms = 4;
UPDATE properties SET home_size = 'BHK5_PLUS' WHERE home_size IS NULL AND bedrooms >= 5;

-- Search by size: GET /properties?homeSize=
CREATE INDEX ix_properties_home_size ON properties (home_size);
