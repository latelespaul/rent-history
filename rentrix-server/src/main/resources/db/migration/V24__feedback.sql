-- =============================================================================
-- Feedback from the "Contact us" form (landing page and /contact).
-- One row per message: optional name and email (needed only for a reply), a
-- type (FEEDBACK, BUG, IDEA, OTHER) and the message. Not tied to a user: a
-- signed-in visitor's name and email are only prefilled in the form.
-- A filled honeypot field stores nothing. Same portable subset as V1 (D-10).
-- =============================================================================
CREATE TABLE feedback (
    id              BIGINT            NOT NULL,
    name            VARCHAR(80),
    email           VARCHAR(320),
    type            VARCHAR(16)       NOT NULL,
    message         VARCHAR(2000)     NOT NULL,
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_feedback PRIMARY KEY (id)
);

CREATE INDEX ix_feedback_created ON feedback (created_at);
