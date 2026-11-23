-- =============================================================================
-- Phone codes sent through the Telegram Gateway (SYSTEM_DESIGN.md D-146). A
-- challenge now records its channel: SMS (an SMS provider or the preview admin
-- relay, D-137) or TELEGRAM (Telegram shows the code in the user's Telegram
-- app). Telegram's request_id is kept so the entered code can be reported back
-- (checkVerificationStatus). Existing rows are SMS. Same portable subset as V1
-- (D-10).
-- =============================================================================
ALTER TABLE phone_otp_challenges ADD COLUMN channel VARCHAR(16) DEFAULT 'SMS' NOT NULL;
ALTER TABLE phone_otp_challenges ADD COLUMN gateway_request_id VARCHAR(64);
