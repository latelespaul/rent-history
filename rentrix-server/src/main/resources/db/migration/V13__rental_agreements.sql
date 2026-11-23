-- =============================================================================
-- Rental agreements (SYSTEM_DESIGN.md §5.20, D-123 – D-131; database/schema.md
-- §6). One row per uploaded agreement: the fields the uploader typed (Aadhaar-
-- like numbers already masked, D-131), the file's SHA-256, and the encrypted
-- file's random key in PRIVATE storage (null once retention deleted the file).
-- checks is plain TEXT holding JSON written and read whole by the API (D-10
-- allows no vendor JSON functions). Same portable subset as V1 (D-10).
-- =============================================================================
CREATE TABLE rental_agreements (
    id                         BIGINT            NOT NULL,
    public_id                  CHAR(36)          NOT NULL,
    uploader_id                BIGINT            NOT NULL,
    uploader_side              VARCHAR(16)       NOT NULL,
    tenancy_id                 BIGINT,
    application_id             BIGINT,
    city_code                  VARCHAR(8)        NOT NULL,
    anchor_kind                VARCHAR(24)       NOT NULL,
    anchor_number              VARCHAR(64),
    anchor_number_key          VARCHAR(64),
    anchor_office              VARCHAR(120),
    anchor_year                INTEGER,
    anchor_date                DATE,
    grn                        VARCHAR(40),
    stamp_amount_inr           BIGINT,
    registration_fee_inr       BIGINT,
    stamp_purchaser            VARCHAR(120),
    stamp_article              VARCHAR(160),
    landlord_name              VARCHAR(120),
    tenant_name                VARCHAR(120),
    address_text               VARCHAR(500),
    pincode                    CHAR(6),
    society_text               VARCHAR(160),
    tower_text                 VARCHAR(60),
    unit_text                  VARCHAR(60),
    monthly_rent_inr           BIGINT,
    deposit_inr                BIGINT,
    escalation_pct             NUMERIC(5,2),
    start_date                 DATE,
    end_date                   DATE,
    term_months                INTEGER,
    extractor                  VARCHAR(16)       NOT NULL,
    sha256                     CHAR(64)          NOT NULL,
    content_type               VARCHAR(64)       NOT NULL,
    size_bytes                 BIGINT            NOT NULL,
    page_count                 INTEGER,
    storage_key                VARCHAR(255),
    pdf_producer               VARCHAR(200),
    pdf_modified_at            ${timestamp_type},
    uploader_confirmed_at      ${timestamp_type},
    counterparty_confirmed_at  ${timestamp_type},
    counterparty_confirmed_by  BIGINT,
    dispute_reason             VARCHAR(1000),
    checks                     TEXT,
    tier                       VARCHAR(16)       NOT NULL,
    anchor_result              VARCHAR(16),
    anchor_checked_by          BIGINT,
    anchor_checked_at          ${timestamp_type},
    anchor_note                VARCHAR(1000),
    flags                      VARCHAR(200),
    status                     VARCHAR(16)       NOT NULL,
    consent_at                 ${timestamp_type} NOT NULL,
    decided_at                 ${timestamp_type},
    purge_after                ${timestamp_type},
    file_deleted_at            ${timestamp_type},
    created_at                 ${timestamp_type} NOT NULL,
    updated_at                 ${timestamp_type} NOT NULL,
    CONSTRAINT pk_rental_agreements PRIMARY KEY (id),
    CONSTRAINT uq_rental_agreements_public_id UNIQUE (public_id),
    CONSTRAINT fk_rental_agreements_uploader FOREIGN KEY (uploader_id) REFERENCES users (id),
    CONSTRAINT fk_rental_agreements_tenancy FOREIGN KEY (tenancy_id) REFERENCES tenancies (id),
    CONSTRAINT fk_rental_agreements_application FOREIGN KEY (application_id) REFERENCES verification_applications (id),
    CONSTRAINT fk_rental_agreements_city FOREIGN KEY (city_code) REFERENCES cities (code),
    CONSTRAINT fk_rental_agreements_counterparty FOREIGN KEY (counterparty_confirmed_by) REFERENCES users (id),
    CONSTRAINT fk_rental_agreements_anchor_checker FOREIGN KEY (anchor_checked_by) REFERENCES users (id)
);
CREATE INDEX ix_rental_agreements_tenancy ON rental_agreements (tenancy_id);
CREATE INDEX ix_rental_agreements_application ON rental_agreements (application_id);
CREATE INDEX ix_rental_agreements_uploader ON rental_agreements (uploader_id, created_at);
CREATE INDEX ix_rental_agreements_sha256 ON rental_agreements (sha256);
CREATE INDEX ix_rental_agreements_anchor ON rental_agreements (anchor_kind, anchor_number_key);
CREATE INDEX ix_rental_agreements_purge ON rental_agreements (purge_after);
