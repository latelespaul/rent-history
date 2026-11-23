-- =============================================================================
-- The review form marker (D-157): a review written with the form keeps the
-- form's rules on every edit (optional comment), whatever it stores. Existing
-- rows were written before the form and keep the D-43 review text rule.
-- Same portable subset as V1 (D-10).
-- =============================================================================
ALTER TABLE reviews ADD COLUMN form_review BOOLEAN DEFAULT FALSE NOT NULL;
