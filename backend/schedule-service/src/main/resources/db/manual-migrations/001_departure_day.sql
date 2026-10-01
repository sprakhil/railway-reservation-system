-- MySQL 8; run once against the schedule-service database before service startup.
-- If Hibernate ddl-auto=update already added the column, omit ALTER TABLE.
ALTER TABLE schedules ADD COLUMN departure_day_number INT NULL;
UPDATE schedules SET departure_day_number = day_number WHERE departure_day_number IS NULL;

-- Before upgrading, audit every existing timetable for consecutive stop_sequence,
-- increasing distance_km and chronological arrival/departure day/time pairs.
-- A legacy stop arriving before midnight and departing after midnight needs an
-- explicitly corrected departure_day_number; no silent day inference is performed.

-- This must return no rows before adding the new station uniqueness constraint.
-- Resolve an invalid legacy timetable deliberately while bookings are disabled;
-- never delete or renumber stops automatically, because existing bookings use them.
SELECT train_number, station_code, COUNT(*) AS duplicate_count
FROM schedules
GROUP BY train_number, station_code
HAVING COUNT(*) > 1;

-- Omit this statement if Hibernate already created this named constraint.
-- It fails safely rather than discarding data if duplicate stations still exist.
ALTER TABLE schedules ADD CONSTRAINT uk_schedule_train_station UNIQUE (train_number, station_code);
