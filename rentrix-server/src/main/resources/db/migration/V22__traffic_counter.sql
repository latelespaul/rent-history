-- =============================================================================
-- Privacy-first traffic counter (SYSTEM_DESIGN §10, D-175).
-- page_view_daily: how many views a normalised page got per day and source.
-- visitor_daily: one salted hash per visitor per day, to count visitors once.
-- The hash is SHA-256(daily random salt kept in memory only + IP + user agent);
-- the salt is never stored, so a hash cannot be reversed or linked across days.
-- No user id, no IP, no user agent, no invite code, no cookie: nothing here can
-- be joined to users, reviews or tenancies. Same portable subset as V1 (D-10).
-- =============================================================================
CREATE TABLE page_view_daily (
    seen_on         DATE          NOT NULL,
    path            VARCHAR(120)  NOT NULL,
    source          VARCHAR(40)   NOT NULL,
    views           BIGINT        NOT NULL,
    CONSTRAINT pk_page_view_daily PRIMARY KEY (seen_on, path, source)
);

CREATE TABLE visitor_daily (
    seen_on         DATE          NOT NULL,
    visitor_hash    CHAR(64)      NOT NULL,
    CONSTRAINT pk_visitor_daily PRIMARY KEY (seen_on, visitor_hash)
);
