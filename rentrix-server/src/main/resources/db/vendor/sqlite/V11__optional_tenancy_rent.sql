-- =============================================================================
-- The rent of a tenancy is optional (captain 2026-11-23 09:55): a tenant adding
-- their home may leave it out, shown "Rent not shared". SQLite cannot drop NOT
-- NULL in place, so tenancies is rebuilt the way the SQLite docs prescribe
-- (sqlite.org/lang_altertable.html, "other kinds of table schema changes"):
-- foreign keys off, new table, copy, drop, rename, indexes, foreign keys on.
-- The columns and constraints are V1's plus V7's verification columns and V9's
-- rent_visibility, unchanged but for initial_rent_inr. Runs outside Flyway's
-- transaction (V11__optional_tenancy_rent.sql.conf); the SAVEPOINT is its own.
-- =============================================================================
PRAGMA foreign_keys = OFF;
SAVEPOINT optional_tenancy_rent;

CREATE TABLE tenancies_v11 (
    id                      BIGINT            NOT NULL,
    public_id               CHAR(36)          NOT NULL,
    property_id             BIGINT            NOT NULL,
    landlord_id             BIGINT            NOT NULL,
    -- null while a landlord-initiated invite waits for a tenant without an account
    tenant_id               BIGINT,
    start_date              DATE              NOT NULL,
    end_date                DATE,
    -- null when the tenant did not share the rent
    initial_rent_inr        BIGINT,
    deposit_inr             BIGINT,
    deposit_refunded_inr    BIGINT,
    status                  VARCHAR(24)       NOT NULL,
    initiated_by            VARCHAR(16)       NOT NULL,
    invite_email            VARCHAR(320),
    decline_reason          VARCHAR(500),
    evidence_level          VARCHAR(16),
    confirmed_at            ${timestamp_type},
    ended_at                ${timestamp_type},
    voided_at               ${timestamp_type},
    void_reason             VARCHAR(500),
    created_at              ${timestamp_type} NOT NULL,
    updated_at              ${timestamp_type} NOT NULL,
    verification_status     VARCHAR(16),
    verification_decided_by BIGINT REFERENCES users (id),
    verification_decided_at ${timestamp_type},
    verification_reason     VARCHAR(1000),
    rent_visibility         VARCHAR(16)       DEFAULT 'OPEN' NOT NULL,
    CONSTRAINT pk_tenancies PRIMARY KEY (id),
    CONSTRAINT uq_tenancies_public_id UNIQUE (public_id),
    CONSTRAINT fk_tenancies_property FOREIGN KEY (property_id) REFERENCES properties (id),
    CONSTRAINT fk_tenancies_landlord FOREIGN KEY (landlord_id) REFERENCES users (id),
    CONSTRAINT fk_tenancies_tenant FOREIGN KEY (tenant_id) REFERENCES users (id),
    CONSTRAINT ck_tenancies_parties CHECK (tenant_id IS NULL OR tenant_id <> landlord_id),
    CONSTRAINT ck_tenancies_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_tenancies_money CHECK (
        (initial_rent_inr IS NULL OR initial_rent_inr > 0)
        AND (deposit_inr IS NULL OR deposit_inr >= 0)
        AND (deposit_refunded_inr IS NULL OR deposit_refunded_inr >= 0))
);

INSERT INTO tenancies_v11 (id, public_id, property_id, landlord_id, tenant_id, start_date, end_date,
    initial_rent_inr, deposit_inr, deposit_refunded_inr, status, initiated_by, invite_email, decline_reason,
    evidence_level, confirmed_at, ended_at, voided_at, void_reason, created_at, updated_at, verification_status,
    verification_decided_by, verification_decided_at, verification_reason, rent_visibility)
SELECT id, public_id, property_id, landlord_id, tenant_id, start_date, end_date,
    initial_rent_inr, deposit_inr, deposit_refunded_inr, status, initiated_by, invite_email, decline_reason,
    evidence_level, confirmed_at, ended_at, voided_at, void_reason, created_at, updated_at, verification_status,
    verification_decided_by, verification_decided_at, verification_reason, rent_visibility
FROM tenancies;

DROP TABLE tenancies;
ALTER TABLE tenancies_v11 RENAME TO tenancies;

CREATE INDEX ix_tenancies_property_status ON tenancies (property_id, status);
CREATE INDEX ix_tenancies_tenant_status ON tenancies (tenant_id, status);
CREATE INDEX ix_tenancies_landlord_status ON tenancies (landlord_id, status);
CREATE INDEX ix_tenancies_pair ON tenancies (landlord_id, tenant_id, start_date);
CREATE INDEX ix_tenancies_verification ON tenancies (verification_status, created_at);

RELEASE optional_tenancy_rent;
PRAGMA foreign_keys = ON;
