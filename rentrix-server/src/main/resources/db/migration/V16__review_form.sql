-- =============================================================================
-- The review form (captain 2026-11-23 21:29; SYSTEM_DESIGN D-157, design note
-- rentrix-docs/design/review-form.md): a 1-5 star rating per parameter of the
-- home or the landlord, "would you recommend", pros and cons. Each parameter
-- the user rated is one row; a skipped parameter has none. Existing reviews
-- keep their single rating_overall and optional rating_a/b/c and gain no rows.
-- (V15 is the edit-own PR's.) Same portable subset as V1 (D-10): table-level
-- constraints only, which MySQL enforces as SQLite and PostgreSQL do.
-- =============================================================================
CREATE TABLE review_criterion_ratings (
    review_id       BIGINT            NOT NULL,
    criterion       VARCHAR(32)       NOT NULL,
    rating          INTEGER           NOT NULL,
    CONSTRAINT pk_review_criterion_ratings PRIMARY KEY (review_id, criterion),
    CONSTRAINT fk_review_criterion_ratings_review FOREIGN KEY (review_id) REFERENCES reviews (id),
    CONSTRAINT ck_review_criterion_ratings_rating CHECK (rating BETWEEN 1 AND 5)
);

-- null = not answered / no text
ALTER TABLE reviews ADD COLUMN would_recommend BOOLEAN;
ALTER TABLE reviews ADD COLUMN pros VARCHAR(1000);
ALTER TABLE reviews ADD COLUMN cons VARCHAR(1000);

-- The previous version of a form review (§5.7); criteria as "WATER_SUPPLY=4,NOISE=2".
ALTER TABLE review_revisions ADD COLUMN criteria VARCHAR(500);
ALTER TABLE review_revisions ADD COLUMN would_recommend BOOLEAN;
ALTER TABLE review_revisions ADD COLUMN pros VARCHAR(1000);
ALTER TABLE review_revisions ADD COLUMN cons VARCHAR(1000);
