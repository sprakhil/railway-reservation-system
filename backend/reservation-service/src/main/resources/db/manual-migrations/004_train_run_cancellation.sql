-- Back up the reservation database and stop all reservation-service instances first.
-- Apply once ONLY for an existing schema using ddl-auto=validate/none.
-- Fresh development schemas / ddl-auto=update create these mappings automatically.
USE railway_reservation_db;
CREATE TABLE train_run_cancellations (
 run_key VARCHAR(80) PRIMARY KEY,
 train_number VARCHAR(10) NOT NULL,
 run_date DATE NOT NULL,
 source_departure_at DATETIME(6) NOT NULL,
 reason VARCHAR(500) NOT NULL,
 cancelled_by VARCHAR(255) NOT NULL,
 cancelled_at DATETIME(6) NOT NULL
) ENGINE=InnoDB;
ALTER TABLE reservations ADD COLUMN operator_cancellation_reason VARCHAR(500) NULL,
 ADD COLUMN operator_cancelled_at DATETIME(6) NULL,
 ADD COLUMN operator_previous_status VARCHAR(255) NULL;
CREATE INDEX idx_reservation_run ON reservations(train_number,run_date);
