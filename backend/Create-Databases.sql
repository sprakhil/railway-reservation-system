-- Run in MySQL Workbench against your local MySQL server.
-- Creates missing databases only. Never drops tables or deletes existing rows.
CREATE DATABASE IF NOT EXISTS railway_auth_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_user_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_train_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_schedule_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_reservation_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_pnr_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_payment_db CHARACTER SET utf8mb4;
CREATE DATABASE IF NOT EXISTS railway_notification_db CHARACTER SET utf8mb4;
