-- =============================================================================
-- Identity verification (SYSTEM_DESIGN.md §5.15, §5.16, D-99 – D-106, D-114).
-- identity_verifications: one row per user per method (AADHAAR, PAN,
-- PHONE_OTP, LINKEDIN). "ID verified" = any of AADHAAR / PAN / PHONE_OTP is
-- VERIFIED; LINKEDIN is a bonus badge only. Only masked data is kept: last4
-- and, for PAN and phone, an HMAC for duplicate detection. A full PAN, any
-- Aadhaar digit beyond the last 4 and an OTP code are never stored.
-- phone_otp_challenges: one row per SMS code sent; the code is kept only as an
-- HMAC. No change to users (phone exists in V1). Same portable subset as V1
-- (D-10); ${timestamp_type} is set by MigrationPlaceholders.
-- =============================================================================
CREATE TABLE identity_verifications (
    id              BIGINT            NOT NULL,
    public_id       CHAR(36)          NOT NULL,
    user_id         BIGINT            NOT NULL,
    method          VARCHAR(16)       NOT NULL,
    status          VARCHAR(16)       NOT NULL,
    last4           VARCHAR(4),
    value_hmac      CHAR(64),
    linkedin_url    VARCHAR(200),
    checked_by      VARCHAR(16),
    provider        VARCHAR(40),
    provider_ref    VARCHAR(100),
    verified_at     ${timestamp_type},
    verified_by     BIGINT,
    reject_code     VARCHAR(32),
    reject_note     VARCHAR(1000),
    revoked_at      ${timestamp_type},
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_identity_verifications PRIMARY KEY (id),
    CONSTRAINT uq_identity_verifications_public_id UNIQUE (public_id),
    CONSTRAINT uq_identity_verifications_user_method UNIQUE (user_id, method),
    CONSTRAINT fk_identity_verifications_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_identity_verifications_verifier FOREIGN KEY (verified_by) REFERENCES users (id)
);
CREATE INDEX ix_identity_verifications_hmac ON identity_verifications (method, value_hmac);
CREATE INDEX ix_identity_verifications_queue ON identity_verifications (status, method, created_at);

CREATE TABLE phone_otp_challenges (
    id              BIGINT            NOT NULL,
    public_id       CHAR(36)          NOT NULL,
    user_id         BIGINT            NOT NULL,
    phone_e164      VARCHAR(16)       NOT NULL,
    phone_hmac      CHAR(64)          NOT NULL,
    code_hmac       CHAR(64)          NOT NULL,
    attempts        INTEGER           NOT NULL DEFAULT 0,
    expires_at      ${timestamp_type} NOT NULL,
    consumed_at     ${timestamp_type},
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_phone_otp_challenges PRIMARY KEY (id),
    CONSTRAINT uq_phone_otp_challenges_public_id UNIQUE (public_id),
    CONSTRAINT fk_phone_otp_challenges_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_phone_otp_attempts CHECK (attempts >= 0 AND attempts <= 5)
);
CREATE INDEX ix_phone_otp_user_created ON phone_otp_challenges (user_id, created_at);
CREATE INDEX ix_phone_otp_phone_created ON phone_otp_challenges (phone_hmac, created_at);
