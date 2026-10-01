-- One-time MySQL 8 upgrade AFTER 001_booking_lifecycle.sql. Stop affected services and back up first.
-- Fresh empty databases may use Hibernate schema creation instead; do not run this on an upgraded schema.
ALTER TABLE reservations
 ADD COLUMN waitlist_deadline DATETIME(6),
 ADD COLUMN waitlist_requested BIT NOT NULL DEFAULT 0,
 ADD COLUMN coach_count INT,
 ADD COLUMN seats_per_coach INT,
 ADD COLUMN retired_status VARCHAR(32),
 ADD INDEX idx_waitlist_queue (inventory_key,status,reservation_id);
ALTER TABLE passengers ADD COLUMN retired_booking_status VARCHAR(32);
-- Preserve obsolete RAC labels for audit. Legacy records are never enrolled in paid WL promotion.
UPDATE reservations SET retired_status='RAC',status='WAITING' WHERE status='RAC';
UPDATE passengers SET retired_booking_status='RAC',booking_status='WAITING' WHERE booking_status='RAC';
ALTER TABLE reservations MODIFY COLUMN status ENUM('PENDING_PAYMENT','CONFIRMED','EXPIRED','WAITING','CANCELLED');
ALTER TABLE passengers MODIFY COLUMN booking_status ENUM('PENDING_PAYMENT','CONFIRMED','EXPIRED','WAITING','CANCELLED');

RENAME TABLE booking_notification_outbox TO booking_notification_outbox_v1_archive;
CREATE TABLE booking_notification_outbox (
 event_id VARCHAR(255) NOT NULL PRIMARY KEY,
 pnr VARCHAR(255),event_type VARCHAR(255),message VARCHAR(4000),
 user_email VARCHAR(255),username VARCHAR(255),train_number VARCHAR(255),date_of_journey VARCHAR(255),
 next_attempt_at DATETIME(6),delivered_at DATETIME(6),attempts INT NOT NULL DEFAULT 0
);
INSERT INTO booking_notification_outbox (event_id,pnr,event_type,message,user_email,username,train_number,date_of_journey,next_attempt_at,delivered_at,attempts)
SELECT CONCAT(pnr,':LEGACY_CONFIRMATION'),pnr,'PAYMENT_RECEIVED','Your demo booking is confirmed.',user_email,username,train_number,date_of_journey,next_attempt_at,delivered_at,attempts
FROM booking_notification_outbox_v1_archive;
