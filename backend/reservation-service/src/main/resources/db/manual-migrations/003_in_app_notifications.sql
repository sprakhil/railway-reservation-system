-- Apply once in railway_reservation_db only if Hibernate DDL_AUTO=validate/none.
-- With DDL_AUTO=update the entity creates this table automatically.
CREATE TABLE user_notifications (
    event_id VARCHAR(120) NOT NULL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    pnr VARCHAR(255) NOT NULL,
    event_type VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message VARCHAR(4000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    INDEX idx_notification_owner_created (username, created_at, event_id),
    INDEX idx_notification_owner_read (username, read_at)
);
