-- =============================================================================
-- Invite friends (design note rentrix-docs/design/invite-friends.md, IF-1, IF-2).
-- invite_codes: one random share code per user, made on the first GET /me/invite.
-- referrals: one row per person who signed up through someone's code; the
-- unique invitee_id makes a person count once, for one inviter.
-- Neither table has a public_id: no row is addressed from outside.
-- Same portable subset as V1 (D-10).
-- =============================================================================
CREATE TABLE invite_codes (
    id              BIGINT            NOT NULL,
    user_id         BIGINT            NOT NULL,
    code            VARCHAR(16)       NOT NULL,
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_invite_codes PRIMARY KEY (id),
    CONSTRAINT uq_invite_codes_user UNIQUE (user_id),
    CONSTRAINT uq_invite_codes_code UNIQUE (code),
    CONSTRAINT fk_invite_codes_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE referrals (
    id              BIGINT            NOT NULL,
    inviter_id      BIGINT            NOT NULL,
    invitee_id      BIGINT            NOT NULL,
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_referrals PRIMARY KEY (id),
    CONSTRAINT uq_referrals_invitee UNIQUE (invitee_id),
    CONSTRAINT fk_referrals_inviter FOREIGN KEY (inviter_id) REFERENCES users (id),
    CONSTRAINT fk_referrals_invitee FOREIGN KEY (invitee_id) REFERENCES users (id)
);
CREATE INDEX ix_referrals_inviter ON referrals (inviter_id);
