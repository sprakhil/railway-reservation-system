-- One-time upgrade of the ORIGINAL railway_reservation_db (MySQL 8).
-- Stop services, back up the database and review this script before execution.
-- Do not run against an already upgraded schema. MySQL DDL auto-commits.
-- Legacy total_fare and duplicate dummy seats are preserved, not migrated into inventory.
ALTER TABLE reservations
  ADD COLUMN quoted_total_fare DECIMAL(12,2) NULL,
  ADD COLUMN fare_per_passenger DECIMAL(12,2) NULL,
  ADD COLUMN base_fare DECIMAL(12,2) NULL,
  ADD COLUMN rate_per_km DECIMAL(12,2) NULL,
  ADD COLUMN minimum_fare DECIMAL(12,2) NULL,
  ADD COLUMN cancellation_fee DECIMAL(12,2) NULL,
  ADD COLUMN refund_amount DECIMAL(12,2) NULL,
  ADD COLUMN refund_status VARCHAR(255) NULL,
  ADD COLUMN currency VARCHAR(255) NULL,
  ADD COLUMN user_email VARCHAR(255) NULL,
  ADD COLUMN from_station VARCHAR(255) NULL,
  ADD COLUMN to_station VARCHAR(255) NULL,
  ADD COLUMN seat_class VARCHAR(255) NULL,
  ADD COLUMN run_date DATE NULL,
  ADD COLUMN from_sequence INT NULL,
  ADD COLUMN to_sequence INT NULL,
  ADD COLUMN distance_km INT NULL,
  ADD COLUMN departure_at DATETIME(6) NULL,
  ADD COLUMN hold_expires_at DATETIME(6) NULL,
  ADD COLUMN inventory_key VARCHAR(255) NULL,
  ADD COLUMN booking_key VARCHAR(180) NULL,
  ADD COLUMN request_hash VARCHAR(255) NULL,
  ADD COLUMN razorpay_payment_id VARCHAR(255) NULL,
  ADD COLUMN razorpay_order_id VARCHAR(255) NULL,
  ADD COLUMN data_version INT NOT NULL DEFAULT 0,
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
  ADD UNIQUE KEY uk_reservation_booking_key (booking_key),
  ADD UNIQUE KEY uk_reservation_payment (razorpay_payment_id),
  ADD INDEX idx_reservation_inventory (inventory_key,from_sequence,to_sequence,status,hold_expires_at),
  ADD INDEX idx_reservation_username (username);

ALTER TABLE reservations MODIFY COLUMN status ENUM('PENDING_PAYMENT','CONFIRMED','EXPIRED','RAC','WAITING','CANCELLED');
ALTER TABLE passengers MODIFY COLUMN booking_status ENUM('PENDING_PAYMENT','CONFIRMED','EXPIRED','RAC','WAITING','CANCELLED');

CREATE TABLE seat_inventory (
  inventory_key VARCHAR(100) NOT NULL PRIMARY KEY,
  capacity INT NOT NULL
);
CREATE TABLE booking_notification_outbox (
  pnr VARCHAR(255) NOT NULL PRIMARY KEY,
  user_email VARCHAR(255), username VARCHAR(255), train_number VARCHAR(255), date_of_journey VARCHAR(255),
  next_attempt_at DATETIME(6), delivered_at DATETIME(6), attempts INT NOT NULL DEFAULT 0
);
