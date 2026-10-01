-- MANUAL, ONE-TIME MySQL 8 upgrade for the previous auth schema.
-- Stop auth-service and back up its database first. No script runs automatically.
-- Inspect SHOW CREATE TABLE users / otp_verifications before applying.
-- Skip a DDL statement only if the matching column/index already exists.
-- On a new database use Hibernate's initial schema creation for the test/demo profile;
-- this existing-schema upgrade is not a Flyway bootstrap migration.

ALTER TABLE otp_verifications MODIFY COLUMN otp VARCHAR(255) NOT NULL;
ALTER TABLE otp_verifications ADD COLUMN purpose ENUM('REGISTRATION','PASSWORD_RESET') NULL;
ALTER TABLE otp_verifications ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE otp_verifications ADD COLUMN created_at DATETIME(6) NULL;

-- Old OTPs have no trustworthy purpose or hash. Retain rows for audit, but expire them.
UPDATE otp_verifications
SET expiry_time = LEAST(expiry_time, UTC_TIMESTAMP(6)),
    purpose = 'REGISTRATION', created_at = UTC_TIMESTAMP(6)
WHERE purpose IS NULL;
ALTER TABLE otp_verifications MODIFY COLUMN purpose ENUM('REGISTRATION','PASSWORD_RESET') NOT NULL;
ALTER TABLE otp_verifications MODIFY COLUMN created_at DATETIME(6) NOT NULL;
CREATE INDEX ix_otp_email_purpose_expiry ON otp_verifications (email, purpose, expiry_time);

-- Inspect this result and resolve duplicates manually before creating the index.
SELECT provider_id, COUNT(*) AS duplicate_count FROM users
WHERE provider_id IS NOT NULL GROUP BY provider_id HAVING COUNT(*) > 1;
CREATE UNIQUE INDEX ux_users_provider_id ON users (provider_id);

-- No users/passwords/refresh tokens are deleted or reset by this migration.
-- Existing verification flags are supported. New verification sets both flags.
