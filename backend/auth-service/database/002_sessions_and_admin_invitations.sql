-- One-time MySQL 8 upgrade after upgrade-existing-auth.sql. Stop auth-service and back up first.
ALTER TABLE refresh_tokens ADD COLUMN session_id VARCHAR(36), ADD UNIQUE KEY uk_refresh_session (session_id);
-- Existing sessions have no sid and require sign-in again. No passwords/accounts are changed.
CREATE TABLE admin_invitations (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 token_hash VARCHAR(64) NOT NULL UNIQUE,
 email VARCHAR(255) NOT NULL,invited_by VARCHAR(255) NOT NULL,accepted_by VARCHAR(255),
 created_at DATETIME(6),expires_at DATETIME(6),accepted_at DATETIME(6),revoked_at DATETIME(6)
);
