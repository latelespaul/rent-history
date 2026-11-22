-- =============================================================================
-- Location hierarchy (SYSTEM_DESIGN.md §5.17 – §5.19, D-107 – D-110, D-114).
-- City -> Area (with PIN codes) -> Society -> Tower/Block -> Wing -> Unit, where
-- the property row is the unit. Society, tower and wing are optional levels: a
-- standalone house or building has an area and no society; a tower may have no
-- wings. Rows proposed by a landlord start PENDING (D-109); rows are never
-- hard-deleted, HIDDEN keeps existing properties readable.
-- properties gains four nullable FKs. SQLite cannot add a NOT NULL column without
-- a default, so area_id is nullable here and the API requires it (D-110).
-- Inline REFERENCES in ADD COLUMN is enforced by SQLite and PostgreSQL; MySQL
-- ignores it, so db/vendor/mysql/V5_1__location_fks.sql adds the four FKs there.
-- No backfill: no deployment has property rows before B4 lands (D-114).
-- Same portable subset as V1 (D-10); ${timestamp_type} is set by MigrationPlaceholders.
-- =============================================================================
CREATE TABLE areas (
    id              BIGINT            NOT NULL,
    public_id       CHAR(36)          NOT NULL,
    city_code       VARCHAR(8)        NOT NULL,
    name            VARCHAR(120)      NOT NULL,
    -- lower-cased, trimmed, single-spaced copy of name; unique per city
    name_key        VARCHAR(120)      NOT NULL,
    aliases         VARCHAR(255),
    status          VARCHAR(16)       NOT NULL,
    created_by      BIGINT,
    is_demo         BOOLEAN           NOT NULL DEFAULT FALSE,
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_areas PRIMARY KEY (id),
    CONSTRAINT uq_areas_public_id UNIQUE (public_id),
    CONSTRAINT uq_areas_city_name UNIQUE (city_code, name_key),
    CONSTRAINT fk_areas_city FOREIGN KEY (city_code) REFERENCES cities (code),
    CONSTRAINT fk_areas_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
-- Area lookup by name: GET /areas?city=&q=
CREATE INDEX ix_areas_city_status ON areas (city_code, status, name_key);

-- One area can span several PIN codes and one PIN code can cover several areas (D-108).
CREATE TABLE area_pincodes (
    area_id         BIGINT            NOT NULL,
    pincode         CHAR(6)           NOT NULL,
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_area_pincodes PRIMARY KEY (area_id, pincode),
    CONSTRAINT fk_area_pincodes_area FOREIGN KEY (area_id) REFERENCES areas (id)
);
-- Area lookup by PIN code: GET /areas?pincode=
CREATE INDEX ix_area_pincodes_pincode ON area_pincodes (pincode);

CREATE TABLE societies (
    id              BIGINT            NOT NULL,
    public_id       CHAR(36)          NOT NULL,
    area_id         BIGINT            NOT NULL,
    -- copy of the area's city, for city-wide browse
    city_code       VARCHAR(8)        NOT NULL,
    name            VARCHAR(160)      NOT NULL,
    name_key        VARCHAR(160)      NOT NULL,
    developer       VARCHAR(120),
    kind            VARCHAR(24)       NOT NULL,
    street          VARCHAR(200),
    status          VARCHAR(16)       NOT NULL,
    created_by      BIGINT,
    is_demo         BOOLEAN           NOT NULL DEFAULT FALSE,
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_societies PRIMARY KEY (id),
    CONSTRAINT uq_societies_public_id UNIQUE (public_id),
    -- also the prefix index for browse by area
    CONSTRAINT uq_societies_area_name UNIQUE (area_id, name_key),
    CONSTRAINT fk_societies_area FOREIGN KEY (area_id) REFERENCES areas (id),
    CONSTRAINT fk_societies_city FOREIGN KEY (city_code) REFERENCES cities (code),
    CONSTRAINT fk_societies_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);
CREATE INDEX ix_societies_city_status ON societies (city_code, status, name_key);

CREATE TABLE society_towers (
    id              BIGINT            NOT NULL,
    public_id       CHAR(36)          NOT NULL,
    society_id      BIGINT            NOT NULL,
    name            VARCHAR(60)       NOT NULL,
    name_key        VARCHAR(60)       NOT NULL,
    kind            VARCHAR(8)        NOT NULL,
    sort_order      INTEGER           NOT NULL DEFAULT 0,
    status          VARCHAR(16)       NOT NULL,
    created_by      BIGINT,
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_society_towers PRIMARY KEY (id),
    CONSTRAINT uq_society_towers_public_id UNIQUE (public_id),
    CONSTRAINT uq_society_towers_name UNIQUE (society_id, name_key),
    CONSTRAINT fk_society_towers_society FOREIGN KEY (society_id) REFERENCES societies (id),
    CONSTRAINT fk_society_towers_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

CREATE TABLE society_wings (
    id              BIGINT            NOT NULL,
    public_id       CHAR(36)          NOT NULL,
    tower_id        BIGINT            NOT NULL,
    name            VARCHAR(60)       NOT NULL,
    name_key        VARCHAR(60)       NOT NULL,
    sort_order      INTEGER           NOT NULL DEFAULT 0,
    status          VARCHAR(16)       NOT NULL,
    created_by      BIGINT,
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_society_wings PRIMARY KEY (id),
    CONSTRAINT uq_society_wings_public_id UNIQUE (public_id),
    CONSTRAINT uq_society_wings_name UNIQUE (tower_id, name_key),
    CONSTRAINT fk_society_wings_tower FOREIGN KEY (tower_id) REFERENCES society_towers (id),
    CONSTRAINT fk_society_wings_created_by FOREIGN KEY (created_by) REFERENCES users (id)
);

ALTER TABLE properties ADD COLUMN area_id    BIGINT REFERENCES areas (id);
ALTER TABLE properties ADD COLUMN society_id BIGINT REFERENCES societies (id);
ALTER TABLE properties ADD COLUMN tower_id   BIGINT REFERENCES society_towers (id);
ALTER TABLE properties ADD COLUMN wing_id    BIGINT REFERENCES society_wings (id);
-- Area page and GET /properties?area=: published homes of one area
CREATE INDEX ix_properties_area_status ON properties (area_id, status);
-- Society page and GET /properties?society=&tower=: homes per society, tower, wing
CREATE INDEX ix_properties_society_status ON properties (society_id, status, tower_id, wing_id);
