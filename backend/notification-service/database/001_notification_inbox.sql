-- One-time MySQL 8 addition for an existing notification-service database. Review/back up first.
CREATE TABLE notification_deliveries (
 event_id VARCHAR(150) NOT NULL PRIMARY KEY,
 pnr VARCHAR(255),user_email VARCHAR(255),username VARCHAR(255),train_number VARCHAR(255),date_of_journey VARCHAR(255),
 event_type VARCHAR(255),message VARCHAR(4000),attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(6),sent_at DATETIME(6)
);
