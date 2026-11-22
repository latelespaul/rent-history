-- =============================================================================
-- Verification core (SYSTEM_DESIGN.md §5.13, §7.3, D-87 – D-91, D-98, D-114).
-- verification_applications: one row per user per role (TENANT, LANDLORD);
-- DRAFT -> PENDING -> APPROVED / REJECTED, resubmit with revision + 1. Written
-- in the design-v3 shape: no LinkedIn columns (LinkedIn is an
-- identity_verifications row, D-106). verification_events: append-only
-- history of every submit and decision. properties gains the last listing
-- decision (D-92). moderation_actions is dropped and recreated with a
-- nullable target_id and a target_ref (public_id or city code), so cities and
-- other non-BIGINT targets fit (D-87); no released code wrote it before this
-- migration, and SQLite cannot relax NOT NULL in place. Same portable subset
-- as V1 (D-10); ${timestamp_type} is set by MigrationPlaceholders.
-- =============================================================================
CREATE TABLE verification_applications (
    id              BIGINT            NOT NULL,
    public_id       CHAR(36)          NOT NULL,
    user_id         BIGINT            NOT NULL,
    role            VARCHAR(16)       NOT NULL,
    status          VARCHAR(16)       NOT NULL,
    revision        INTEGER           NOT NULL DEFAULT 1,
    -- comma list set by the API (DUPLICATE_ID, DUPLICATE_PHONE); admins only
    flags           VARCHAR(200),
    submitted_at    ${timestamp_type},
    decided_at      ${timestamp_type},
    decided_by      BIGINT,
    decision_code   VARCHAR(32),
    decision_note   VARCHAR(1000),
    -- optimistic lock: a concurrent decision or resubmit loses with 409
    version         INTEGER           NOT NULL DEFAULT 0,
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_verification_applications PRIMARY KEY (id),
    CONSTRAINT uq_verification_applications_public_id UNIQUE (public_id),
    CONSTRAINT uq_verification_applications_user_role UNIQUE (user_id, role),
    CONSTRAINT fk_verification_applications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_verification_applications_decider FOREIGN KEY (decided_by) REFERENCES users (id),
    CONSTRAINT ck_verification_applications_revision CHECK (revision >= 1)
);
-- Admin approvals queue: GET /admin/applications?role=&status=PENDING, oldest first.
CREATE INDEX ix_verification_applications_queue ON verification_applications (status, role, submitted_at);

CREATE TABLE verification_events (
    id              BIGINT            NOT NULL,
    application_id  BIGINT            NOT NULL,
    revision        INTEGER           NOT NULL,
    -- SUBMITTED | APPROVED | REJECTED | REVOKED | IDENTITY_CHECKED
    event           VARCHAR(24)       NOT NULL,
    actor_id        BIGINT            NOT NULL,
    code            VARCHAR(32),
    note            VARCHAR(1000),
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_verification_events PRIMARY KEY (id),
    CONSTRAINT fk_verification_events_application FOREIGN KEY (application_id) REFERENCES verification_applications (id),
    CONSTRAINT fk_verification_events_actor FOREIGN KEY (actor_id) REFERENCES users (id)
);
-- History per application, and the 3 submits / 24 h limit (D-90).
CREATE INDEX ix_verification_events_application ON verification_events (application_id, event, created_at);

-- Last listing decision (D-92). reviewed_by is an admin user id; no FK, as
-- MySQL ignores inline REFERENCES on ADD COLUMN and the vendor files come with
-- the location migration.
ALTER TABLE properties ADD COLUMN review_reason VARCHAR(1000);
ALTER TABLE properties ADD COLUMN reviewed_by BIGINT;
ALTER TABLE properties ADD COLUMN reviewed_at ${timestamp_type};

DROP TABLE moderation_actions;

CREATE TABLE moderation_actions (
    id              BIGINT            NOT NULL,
    admin_id        BIGINT            NOT NULL,
    action          VARCHAR(32)       NOT NULL,
    target_type     VARCHAR(24)       NOT NULL,
    target_id       BIGINT,
    -- the target's public_id, or the city code for CITY
    target_ref      VARCHAR(36)       NOT NULL,
    report_id       BIGINT,
    reason          VARCHAR(1000),
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_moderation_actions PRIMARY KEY (id),
    CONSTRAINT fk_moderation_actions_admin FOREIGN KEY (admin_id) REFERENCES users (id),
    CONSTRAINT fk_moderation_actions_report FOREIGN KEY (report_id) REFERENCES reports (id)
);
CREATE INDEX ix_moderation_actions_target ON moderation_actions (target_type, target_ref, created_at);
