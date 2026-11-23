-- =============================================================================
-- The rent of a tenancy is optional (captain 2026-11-23 09:55): a tenant adding
-- their home may leave it out, shown "Rent not shared". The CHECK of V1
-- (initial_rent_inr > 0) already lets NULL through. Vendor files (D-10): SQLite
-- cannot drop NOT NULL in place, so each vendor has its own V11.
-- =============================================================================
ALTER TABLE tenancies ALTER COLUMN initial_rent_inr DROP NOT NULL;
