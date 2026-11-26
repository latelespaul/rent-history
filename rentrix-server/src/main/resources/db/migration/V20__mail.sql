-- =============================================================================
-- Mail through Resend (SYSTEM_DESIGN.md D-172): the welcome email on sign-up
-- and the admin "review reminder", one email per user.
-- users.mail_opt_out_at: set by the unsubscribe link (NULL = subscribed);
-- opted-out users get no welcome or reminder emails (tenancy and review
-- emails about their own items still go out).
-- mail_sends: one row per user and campaign ("welcome", "review-reminder"),
-- so a second run never mails the same user the same campaign twice.
-- Same portable subset as V1 (D-10).
-- =============================================================================
ALTER TABLE users ADD COLUMN mail_opt_out_at ${timestamp_type};

CREATE TABLE mail_sends (
    id              BIGINT            NOT NULL,
    user_id         BIGINT            NOT NULL,
    campaign        VARCHAR(40)       NOT NULL,
    created_at      ${timestamp_type} NOT NULL,
    updated_at      ${timestamp_type} NOT NULL,
    CONSTRAINT pk_mail_sends PRIMARY KEY (id),
    CONSTRAINT uq_mail_sends_user_campaign UNIQUE (user_id, campaign),
    CONSTRAINT fk_mail_sends_user FOREIGN KEY (user_id) REFERENCES users (id)
);
