-- Repair missing/invalid coordinates in existing Railwize catalogue stations.
-- Uses the same approximate station coordinates as ADD-MAJOR-CITY-ROUTES.sql.
-- Back up railway_train_db, stop train-service, then run this whole file in MySQL 8.
-- Valid nonzero coordinate pairs, names, IDs, trains, schedules and bookings are preserved.
-- Unknown/custom station codes are reported; enter their coordinates in Admin > Stations.
SET NAMES utf8mb4;
USE railway_train_db;
DROP TEMPORARY TABLE IF EXISTS rw_station_location_repair;
CREATE TEMPORARY TABLE rw_station_location_repair (
 code VARCHAR(10) PRIMARY KEY, latitude DOUBLE NOT NULL, longitude DOUBLE NOT NULL);
INSERT INTO rw_station_location_repair VALUES
 ('ADI',23.026,72.602),
 ('AGC',27.158,77.989),
 ('AII',26.456,74.638),
 ('AJJ',13.077,79.671),
 ('AK',20.722,77.004),
 ('ALJN',27.888,78.074),
 ('ALLP',9.49,76.327),
 ('ANND',22.563,72.966),
 ('ANVT',28.65,77.315),
 ('ASN',23.691,86.974),
 ('ASR',31.634,74.872),
 ('BAM',19.298,84.805),
 ('BBS',20.266,85.843),
 ('BDTS',19.062,72.84),
 ('BEAS',31.516,75.289),
 ('BHC',21.058,86.497),
 ('BKSC',23.663,86.151),
 ('BPL',23.268,77.413),
 ('BRC',22.31,73.181),
 ('BSB',25.327,82.986),
 ('BSL',21.046,75.789),
 ('BSP',22.057,82.172),
 ('BVI',19.23,72.857),
 ('BZA',16.519,80.619),
 ('CAN',11.872,75.369),
 ('CBE',10.995,76.967),
 ('CDG',30.702,76.821),
 ('CLT',11.245,75.781),
 ('CNB',26.454,80.35),
 ('CSMT',18.93985,72.83544),
 ('CTC',20.465,85.9),
 ('DDU',25.282,83.12),
 ('DHN',23.791,86.429),
 ('DLI',28.661,77.228),
 ('DMM',14.413,77.722),
 ('DURG',21.2,81.29),
 ('ED',11.328,77.727),
 ('ERS',9.97,76.291),
 ('ET',22.612,77.758),
 ('GAYA',24.807,85.0),
 ('GHY',26.182,91.751),
 ('GTL',15.172,77.365),
 ('GZB',28.654,77.427),
 ('HWH',22.584,88.343),
 ('HYB',17.392,78.467),
 ('INDB',22.717,75.869),
 ('JAT',32.706,74.879),
 ('JP',26.92,75.787),
 ('JTJ',12.571,78.571),
 ('JU',26.283,73.022),
 ('JUC',31.326,75.591),
 ('KGP',22.34,87.325),
 ('KJM',13.001,77.675),
 ('KJT',18.91,73.32),
 ('KKDE',29.969,76.852),
 ('KOTA',25.224,75.88),
 ('KPD',12.972,79.138),
 ('KTHU',32.386,75.526),
 ('KYN',19.236,73.13),
 ('KZJ',17.974,79.506),
 ('LDH',30.912,75.848),
 ('LJN',26.831,80.922),
 ('LNL',18.75,73.407),
 ('MAJN',12.867,74.879),
 ('MAO',15.267,73.97),
 ('MAS',13.083,80.275),
 ('MLDT',25.01,88.14),
 ('MMCT',18.9697,72.8194),
 ('MS',13.079,80.261),
 ('NBQ',26.478,90.558),
 ('NDLS',28.6428,77.21908),
 ('NGP',21.153,79.088),
 ('NJP',26.683,88.444),
 ('NLR',14.453,79.988),
 ('NZM',28.58917,77.25417),
 ('OGL',15.497,80.06),
 ('PGT',10.799,76.643),
 ('PNBE',25.603,85.137),
 ('PNP',29.391,76.97),
 ('PNVL',18.99,73.121),
 ('PRYJ',25.445,81.824),
 ('PTKC',32.269,75.642),
 ('PUNE',18.528,73.874),
 ('QLN',8.886,76.596),
 ('R',21.251,81.63),
 ('RC',16.206,77.347),
 ('RJY',16.983,81.784),
 ('RN',17.0,73.337),
 ('RNC',23.35,85.336),
 ('ROU',22.227,84.863),
 ('RTM',23.331,75.05),
 ('RU',13.635,79.503),
 ('SA',11.671,78.114),
 ('SBC',12.979,77.565),
 ('SBT',23.072,72.588),
 ('SC',17.434,78.502),
 ('SDAH',22.567,88.37),
 ('SRR',10.758,76.272),
 ('ST',21.204,72.841),
 ('SUR',17.665,75.892),
 ('SWM',26.018,76.359),
 ('TATA',22.769,86.198),
 ('TCR',10.514,76.208),
 ('TVC',8.487,76.952),
 ('UJN',23.179,75.78),
 ('UMB',30.338,76.827),
 ('VGLJ',25.444,78.552),
 ('VKB',17.336,77.905),
 ('VSKP',17.722,83.289),
 ('WADI',17.054,76.991),
 ('YPR',13.023,77.551);
START TRANSACTION;
-- Preview the exact rows that will be repaired, including their old values.
SELECT s.station_code, s.station_name, s.latitude AS previous_latitude,
 s.longitude AS previous_longitude, r.latitude AS repaired_latitude, r.longitude AS repaired_longitude
FROM stations s JOIN rw_station_location_repair r ON s.station_code=r.code
WHERE s.latitude IS NULL OR s.longitude IS NULL OR (s.latitude=0 AND s.longitude=0)
 OR s.latitude NOT BETWEEN -90 AND 90 OR s.longitude NOT BETWEEN -180 AND 180;
UPDATE stations s JOIN rw_station_location_repair r ON s.station_code=r.code
SET s.latitude=r.latitude, s.longitude=r.longitude
WHERE s.station_id>0 AND (s.latitude IS NULL OR s.longitude IS NULL OR (s.latitude=0 AND s.longitude=0)
 OR s.latitude NOT BETWEEN -90 AND 90 OR s.longitude NOT BETWEEN -180 AND 180);
SELECT ROW_COUNT() AS repaired_station_count;
COMMIT;
-- Any remaining rows need real coordinates from the administrator.
SELECT station_code, station_name, latitude, longitude FROM stations
WHERE latitude IS NULL OR longitude IS NULL OR (latitude=0 AND longitude=0)
 OR latitude NOT BETWEEN -90 AND 90 OR longitude NOT BETWEEN -180 AND 180;
DROP TEMPORARY TABLE rw_station_location_repair;