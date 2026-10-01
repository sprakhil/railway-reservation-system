-- MySQL 8. Run once only when upgrading the original backend payment_records table.
-- Stop payment-service first and back up railway_payment (or your configured database).
-- Confirm payment_records_legacy_20260917 does not already exist before running.
-- Never reconcile original immediate-confirmation bookings as new test payments.
RENAME TABLE payment_records TO payment_records_legacy_20260917;
-- Then run 002_create_test_payment_tables.sql against the same database.
