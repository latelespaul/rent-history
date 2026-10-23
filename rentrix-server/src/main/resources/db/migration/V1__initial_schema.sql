-- =============================================================================
-- V1 — Initial schema
-- =============================================================================
-- Captures the full DB schema as of v0.1.5.
--
-- Fresh DBs: Flyway runs this to create all tables.
-- Existing DBs: Flyway baselines at V1 (skips execution), since tables exist.
--
-- Any changes from v0.1.6 onward become V2__xxx.sql, V3__yyy.sql, etc.
-- =============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. users
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE users
(
   id         BIGINT       NOT NULL AUTO_INCREMENT,
   name       VARCHAR(255) NOT NULL,
   email      VARCHAR(255) NOT NULL,
   password   VARCHAR(255) NOT NULL,
   role       VARCHAR(20)  NOT NULL,
   deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
   created_at DATETIME(6)  NOT NULL,
   updated_at DATETIME(6)  NOT NULL,
   PRIMARY KEY (id),
   UNIQUE KEY idx_users_email (email),
   KEY idx_users_role (role),
   KEY idx_users_deleted (deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. user_details (@MapsId on users)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE user_details
(
   id            BIGINT      NOT NULL,
   first_name    VARCHAR(100),
   last_name     VARCHAR(100),
   date_of_birth DATE,
   telephone     VARCHAR(20),
   address_line  VARCHAR(255),
   street        VARCHAR(255),
   city          VARCHAR(100),
   state         VARCHAR(100),
   zip_code      VARCHAR(20),
   created_at    DATETIME(6) NOT NULL,
   updated_at    DATETIME(6) NOT NULL,
   PRIMARY KEY (id),
   UNIQUE KEY idx_user_details_telephone (telephone),
   KEY idx_user_details_city (city),
   CONSTRAINT fk_user_details_user
      FOREIGN KEY (id) REFERENCES users (id)
         ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. flats
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE flats
(
   id              BIGINT         NOT NULL AUTO_INCREMENT,
   rent            DECIMAL(12, 2) NOT NULL,
   number_of_rooms INT            NOT NULL,
   area            DECIMAL(10, 2),
   floor_number    INT,
   total_floors    INT,
   furnished       BOOLEAN,
   bathrooms       INT,
   parking         BOOLEAN,
   available_from  DATE,
   property_type   VARCHAR(50),
   description     TEXT,
   available       BOOLEAN        NOT NULL DEFAULT TRUE,
   verified        BOOLEAN        NOT NULL DEFAULT FALSE,
   visible         BOOLEAN        NOT NULL DEFAULT FALSE,
   deleted         BOOLEAN        NOT NULL DEFAULT FALSE,
   address_line    VARCHAR(255),
   street          VARCHAR(255),
   city            VARCHAR(100),
   state           VARCHAR(100),
   zip_code        VARCHAR(20),
   owner_id        BIGINT,
   created_by      BIGINT,
   created_at      DATETIME(6)    NOT NULL,
   updated_at      DATETIME(6)    NOT NULL,
   PRIMARY KEY (id),
   KEY idx_flats_city (city),
   KEY idx_flats_state (state),
   KEY idx_flats_rent (rent),
   KEY idx_flats_available (available),
   KEY idx_flats_owner (owner_id),
   KEY idx_flats_created_by (created_by),
   KEY idx_flats_property (property_type),
   KEY idx_flats_visible (visible),
   KEY idx_flats_deleted (deleted),
   CONSTRAINT fk_flats_owner
      FOREIGN KEY (owner_id) REFERENCES users (id)
         ON DELETE SET NULL,
   CONSTRAINT fk_flats_created_by
      FOREIGN KEY (created_by) REFERENCES users (id)
         ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. flat_images
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE flat_images
(
   id                BIGINT        NOT NULL AUTO_INCREMENT,
   flat_id           BIGINT        NOT NULL,
   storage_key       VARCHAR(500)  NOT NULL,
   public_url        VARCHAR(1000) NOT NULL,
   content_type      VARCHAR(100)  NOT NULL,
   size_bytes        BIGINT        NOT NULL,
   original_filename VARCHAR(255),
   display_order     INT           NOT NULL DEFAULT 0,
   is_primary        BOOLEAN       NOT NULL DEFAULT FALSE,
   deleted           BOOLEAN       NOT NULL DEFAULT FALSE,
   created_at        DATETIME(6)   NOT NULL,
   updated_at        DATETIME(6)   NOT NULL,
   PRIMARY KEY (id),
   KEY idx_flat_images_flat (flat_id),
   KEY idx_flat_images_order (flat_id, display_order),
   CONSTRAINT fk_flat_images_flat
      FOREIGN KEY (flat_id) REFERENCES flats (id)
         ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. reviews
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE reviews
(
   id         BIGINT       NOT NULL AUTO_INCREMENT,
   user_id    BIGINT       NOT NULL,
   flat_id    BIGINT       NOT NULL,
   title      VARCHAR(255) NOT NULL,
   content    TEXT         NOT NULL,
   rating     INT          NOT NULL,
   status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
   deleted    BOOLEAN      NOT NULL DEFAULT FALSE,
   created_at DATETIME(6)  NOT NULL,
   updated_at DATETIME(6)  NOT NULL,
   PRIMARY KEY (id),
   UNIQUE KEY uk_reviews_user_flat (user_id, flat_id),
   KEY idx_reviews_flat (flat_id),
   KEY idx_reviews_user (user_id),
   KEY idx_reviews_status (status),
   KEY idx_reviews_deleted (deleted),
   CONSTRAINT fk_reviews_user
      FOREIGN KEY (user_id) REFERENCES users (id)
         ON DELETE CASCADE,
   CONSTRAINT fk_reviews_flat
      FOREIGN KEY (flat_id) REFERENCES flats (id)
         ON DELETE CASCADE,
   CONSTRAINT chk_reviews_rating
      CHECK (rating BETWEEN 1 AND 10)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. review_proofs
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE review_proofs
(
   id                BIGINT       NOT NULL AUTO_INCREMENT,
   review_id         BIGINT       NOT NULL,
   storage_key       VARCHAR(500) NOT NULL,
   original_filename VARCHAR(255),
   content_type      VARCHAR(100) NOT NULL,
   size_bytes        BIGINT       NOT NULL,
   display_order     INT          NOT NULL DEFAULT 0,
   verified          BOOLEAN      NOT NULL DEFAULT FALSE,
   deleted           BOOLEAN      NOT NULL DEFAULT FALSE,
   created_at        DATETIME(6)  NOT NULL,
   updated_at        DATETIME(6)  NOT NULL,
   PRIMARY KEY (id),
   KEY idx_review_proofs_review (review_id),
   KEY idx_review_proofs_order (review_id, display_order),
   CONSTRAINT fk_review_proofs_review
      FOREIGN KEY (review_id) REFERENCES reviews (id)
         ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. rent_history (kept for v0.2.0)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE rent_history
(
   id              BIGINT      NOT NULL AUTO_INCREMENT,
   flat_id         BIGINT      NOT NULL,
   landlord_id     BIGINT,
   tenant_id       BIGINT,
   rent_start_date DATE,
   rent_end_date   DATE,
   created_at      DATETIME(6) NOT NULL,
   updated_at      DATETIME(6) NOT NULL,
   PRIMARY KEY (id),
   KEY idx_rent_history_flat (flat_id),
   KEY idx_rent_history_landlord (landlord_id),
   KEY idx_rent_history_tenant (tenant_id),
   CONSTRAINT fk_rent_history_flat
      FOREIGN KEY (flat_id) REFERENCES flats (id)
         ON DELETE CASCADE,
   CONSTRAINT fk_rent_history_landlord
      FOREIGN KEY (landlord_id) REFERENCES users (id)
         ON DELETE SET NULL,
   CONSTRAINT fk_rent_history_tenant
      FOREIGN KEY (tenant_id) REFERENCES users (id)
         ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;