-- Railwize catalogue display-name update (MySQL 8).
-- Run once in MySQL Workbench if the India catalogue was imported previously.
-- Safe to repeat. Only the seven exact original names are changed.
-- Custom names, train numbers, schedules, capacities and fares are preserved.
-- Historical booking/email snapshots are deliberately not rewritten.
USE railway_train_db;
START TRANSACTION;
UPDATE trains SET train_name = 'Nizamuddin Lucknow Express' WHERE train_number = 'RW901' AND train_name = 'Railwize Demo - Delhi Agra Lucknow Layout Explorer';
UPDATE trains SET train_name = 'Lucknow Varanasi Express' WHERE train_number = 'RW902' AND train_name = 'Railwize Demo - Lucknow Varanasi Connection';
UPDATE trains SET train_name = 'Lucknow Varanasi Intercity' WHERE train_number = 'RW903' AND train_name = 'Railwize Demo - Connection Too Short';
UPDATE trains SET train_name = 'Lucknow Varanasi Night Express' WHERE train_number = 'RW904' AND train_name = 'Railwize Demo - Connection Too Long';
UPDATE trains SET train_name = 'Bandra Sabarmati Express' WHERE train_number = 'RW905' AND train_name = 'Railwize Demo - Bandra Sabarmati Nearby Alternative';
UPDATE trains SET train_name = 'Nizamuddin Lucknow Night Express' WHERE train_number = 'RW906' AND train_name = 'Railwize Demo - Monday Midnight Crossing';
UPDATE trains SET train_name = 'Jammu Katra Express' WHERE train_number = 'RW907' AND train_name = 'Railwize Demo - Jammu Katra Waitlist Lab';
COMMIT;
SELECT train_number, train_name FROM trains WHERE train_number IN ('RW901','RW902','RW903','RW904','RW905','RW906','RW907') ORDER BY train_number;
