-- MySQL 8: additive support-ticket schema update. Back up your databases first.
-- Run with reservation-service and notification-service stopped.
-- Existing service tables must already exist. Safe to rerun; no records are removed.
-- MySQL DDL commits independently: this script is not an atomic migration.
USE railway_reservation_db;
CREATE TABLE IF NOT EXISTS support_tickets (
 id VARCHAR(40) NOT NULL PRIMARY KEY,
 username VARCHAR(255) NOT NULL,
 user_email VARCHAR(255) NOT NULL,
 subject VARCHAR(160) NOT NULL,
 pnr VARCHAR(10) NULL,
 category VARCHAR(30) NOT NULL,
 status VARCHAR(30) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 INDEX idx_support_owner_updated (username,updated_at),
 INDEX idx_support_status_updated (status,updated_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS support_messages (
 id VARCHAR(90) NOT NULL PRIMARY KEY,
 ticket_id VARCHAR(40) NOT NULL,
 author VARCHAR(255) NOT NULL,
 staff BIT(1) NOT NULL,
 system_message BIT(1) NOT NULL,
 body VARCHAR(3000) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 INDEX idx_support_message_ticket (ticket_id,created_at,id)
) ENGINE=InnoDB;
SET @rw_support_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='booking_notification_outbox' AND column_name='support_ticket_id'), 'SELECT 1', 'ALTER TABLE booking_notification_outbox ADD COLUMN support_ticket_id VARCHAR(40) NULL');
PREPARE rw_support_stmt FROM @rw_support_sql; EXECUTE rw_support_stmt; DEALLOCATE PREPARE rw_support_stmt;
SET @rw_support_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user_notifications' AND column_name='support_ticket_id'), 'SELECT 1', 'ALTER TABLE user_notifications ADD COLUMN support_ticket_id VARCHAR(40) NULL');
PREPARE rw_support_stmt FROM @rw_support_sql; EXECUTE rw_support_stmt; DEALLOCATE PREPARE rw_support_stmt;
ALTER TABLE user_notifications MODIFY COLUMN pnr VARCHAR(255) NULL;

USE railway_notification_db;
SET @rw_support_sql = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='notification_logs' AND column_name='support_ticket_id'), 'SELECT 1', 'ALTER TABLE notification_logs ADD COLUMN support_ticket_id VARCHAR(40) NULL');
PREPARE rw_support_stmt FROM @rw_support_sql; EXECUTE rw_support_stmt; DEALLOCATE PREPARE rw_support_stmt;
ALTER TABLE notification_logs MODIFY COLUMN pnr VARCHAR(255) NULL;
