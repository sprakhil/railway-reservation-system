-- RAILWIZE: FINAL CONSOLIDATED CATALOGUE LOADER (MySQL 8.0)
-- Version: 2026-10-15. This ONE file replaces loading the four separate catalogue scripts.
-- CONTENT: original India catalogue + major-city expansion + missing coordinate repair
-- + the seven exact legacy display-name corrections. No SOURCE commands or extra files.
-- Real station identities; historical timetable snapshots and locally defined RW services.
-- All capacities/fares and RW timetables are illustrative, not official railway inventory.
--
-- HOW TO RUN:
-- 1. Create the project databases using backend/Create-Databases.sql if not already created.
-- 2. Start CURRENT train-service and schedule-service once with DDL_AUTO=update, then stop
--    application services. This DATA loader does not create/migrate application tables.
-- 3. Back up your existing databases. Use MySQL 8 on the SAME server for both schemas.
-- 4. Open this file in Workbench and execute the WHOLE file in ONE dedicated connection.
--    Or: mysql -h localhost -P 3306 -u root -p
--    Then: SOURCE C:/path/to/Railwize-india-data/LOAD-FINAL-RAILWIZE-DATA.sql;
-- 5. Check for FINAL CATALOGUE IMPORT SUCCEEDED. Restart the backend and frontend.
--    If an error occurs, stop and read it; do not assume later result sets mean success.
--    Do not use mysql --force. Stop services again before any repeated import.
-- For custom database names replace railway_train_db and railway_schedule_db throughout.
-- Privileges: SELECT, INSERT, UPDATE, CREATE TEMPORARY TABLES, CREATE ROUTINE,
-- ALTER ROUTINE and EXECUTE. The six catalogue tables must exist and use InnoDB.
--
-- EXISTING DATA / RERUNS:
-- Matching complete trains are skipped; missing trains are inserted with their full data.
-- Conflicting endpoints, category, days, classes, fare/capacity or timetable ABORT the
-- entire application-data transaction. Partial trains/orphan schedules are not repaired.
-- Existing IDs, custom names, valid coordinates and published routes remain unchanged.
-- Only missing/invalid coordinate pairs for known codes and seven exact old fixture names
-- are updated. Unknown station coordinates need manual review in Admin > Stations.
-- No DELETE, TRUNCATE, REPLACE, booking/payment/user insertion or inventory reset occurs.
-- There is no need to reload each day: schedules recur on their configured weekdays.
-- The administrative loader procedure and session-private staging tables are disposable.
--
-- QUICK CHECKS AFTER LOADING:
-- Direct: MMCT -> NDLS, or CSMT -> NDLS; expansion trains offer SL, 3A and 2A daily.
-- Nearby: MMCT -> DLI with nearby options enabled can include New Delhi (NDLS).
-- Connection: inspect NZM -> LJN (RW901) + LJN -> BSB (RW902), 90-minute transfer.
--   Additional routes may change ranking or add direct alternatives; results are capped.
-- All layouts: RW901 contains all eight supported classes. Capacity varies by class.
-- Waitlist: RW907 JAT -> SVDK has only two SL berths. Use the normal booking/test-payment
--   flow to fill them, create paid WL, cancel the CNF booking and observe promotion.
-- Overnight: RW906 runs on MONDAY from NZM, with AGC departure on day 2 (Tuesday).
-- Dated cancellation, PDF, mail, notifications and refunds must be exercised through APIs/UI;
--   this script does not manufacture the booking/payment events required by those features.
-- Choose a future boarding date within two calendar months. Date helpers appear at the end.

SET NAMES utf8mb4;
USE railway_train_db;
-- These are session-private staging tables, not application tables.
DROP TEMPORARY TABLE IF EXISTS rw_final_stations;
DROP TEMPORARY TABLE IF EXISTS rw_final_trains;
DROP TEMPORARY TABLE IF EXISTS rw_final_days;
DROP TEMPORARY TABLE IF EXISTS rw_final_classes;
DROP TEMPORARY TABLE IF EXISTS rw_final_stops;
CREATE TEMPORARY TABLE rw_final_stations (
 code VARCHAR(10) PRIMARY KEY,name VARCHAR(255),city VARCHAR(255),state VARCHAR(255),zone VARCHAR(255),latitude DOUBLE,longitude DOUBLE);
CREATE TEMPORARY TABLE rw_final_trains (
 number VARCHAR(10) PRIMARY KEY,name VARCHAR(255),category VARCHAR(32),source_code VARCHAR(10),destination_code VARCHAR(10));
CREATE TEMPORARY TABLE rw_final_days (number VARCHAR(10),day VARCHAR(16),PRIMARY KEY(number,day));
CREATE TEMPORARY TABLE rw_final_classes (
 number VARCHAR(10),class VARCHAR(16),coaches INT,seats INT,base DECIMAL(9,2),rate DECIMAL(9,2),minimum DECIMAL(9,2),PRIMARY KEY(number,class));
CREATE TEMPORARY TABLE rw_final_stops (
 number VARCHAR(10),code VARCHAR(10),sequence INT,arrival TIME,departure TIME,arrival_day INT,departure_day INT,km INT,
 PRIMARY KEY(number,sequence),UNIQUE(number,code));

INSERT INTO rw_final_stations (code,name,city,state,zone,latitude,longitude) VALUES
 ('MMCT','Mumbai Central','Mumbai','Maharashtra','WR',18.9697,72.8194),
 ('CSMT','Chhatrapati Shivaji Maharaj Terminus','Mumbai','Maharashtra','CR',18.93985,72.83544),
 ('DR','Dadar Central','Mumbai','Maharashtra','CR',19.018,72.843),
 ('BDTS','Bandra Terminus','Mumbai','Maharashtra','WR',19.062,72.84),
 ('BVI','Borivali','Mumbai','Maharashtra','WR',19.23,72.857),
 ('KJT','Karjat Junction','Karjat','Maharashtra','CR',18.91,73.32),
 ('LNL','Lonavala','Lonavala','Maharashtra','CR',18.75,73.407),
 ('SVJR','Shivajinagar','Pune','Maharashtra','CR',18.532,73.849),
 ('PUNE','Pune Junction','Pune','Maharashtra','CR',18.528,73.874),
 ('VAPI','Vapi','Vapi','Gujarat','WR',20.373,72.909),
 ('ST','Surat','Surat','Gujarat','WR',21.204,72.841),
 ('BH','Bharuch Junction','Bharuch','Gujarat','WR',21.705,72.996),
 ('BRC','Vadodara Junction','Vadodara','Gujarat','WR',22.31,73.181),
 ('ANND','Anand Junction','Anand','Gujarat','WR',22.563,72.966),
 ('ND','Nadiad Junction','Nadiad','Gujarat','WR',22.691,72.856),
 ('ADI','Ahmedabad Junction','Ahmedabad','Gujarat','WR',23.026,72.602),
 ('SBT','Sabarmati Junction','Ahmedabad','Gujarat','WR',23.072,72.588),
 ('RTM','Ratlam Junction','Ratlam','Madhya Pradesh','WR',23.331,75.05),
 ('NAD','Nagda Junction','Nagda','Madhya Pradesh','WR',23.458,75.416),
 ('KOTA','Kota Junction','Kota','Rajasthan','WCR',25.224,75.88),
 ('SWM','Sawai Madhopur Junction','Sawai Madhopur','Rajasthan','WCR',26.018,76.359),
 ('DPA','Durgapura','Jaipur','Rajasthan','NWR',26.851,75.785),
 ('JP','Jaipur Junction','Jaipur','Rajasthan','NWR',26.92,75.787),
 ('NDLS','New Delhi','New Delhi','Delhi','NR',28.6428,77.21908),
 ('NZM','Hazrat Nizamuddin','New Delhi','Delhi','NR',28.58917,77.25417),
 ('DLI','Delhi Junction','Delhi','Delhi','NR',28.661,77.228),
 ('ANVT','Anand Vihar Terminal','Delhi','Delhi','NR',28.65,77.315),
 ('GZB','Ghaziabad Junction','Ghaziabad','Uttar Pradesh','NR',28.654,77.427),
 ('ALJN','Aligarh Junction','Aligarh','Uttar Pradesh','NCR',27.888,78.074),
 ('TDL','Tundla Junction','Tundla','Uttar Pradesh','NCR',27.21,78.236),
 ('ETW','Etawah Junction','Etawah','Uttar Pradesh','NCR',26.785,79.022),
 ('PHD','Phaphund','Dibiyapur','Uttar Pradesh','NCR',26.665,79.552),
 ('CNB','Kanpur Central','Kanpur','Uttar Pradesh','NCR',26.454,80.35),
 ('ON','Unnao Junction','Unnao','Uttar Pradesh','NR',26.55,80.489),
 ('LJN','Lucknow Junction','Lucknow','Uttar Pradesh','NER',26.831,80.922),
 ('LKO','Lucknow Charbagh','Lucknow','Uttar Pradesh','NR',26.8315,80.923),
 ('AGC','Agra Cantt','Agra','Uttar Pradesh','NCR',27.158,77.989),
 ('PRYJ','Prayagraj Junction','Prayagraj','Uttar Pradesh','NCR',25.445,81.824),
 ('BSB','Varanasi Junction','Varanasi','Uttar Pradesh','NR',25.327,82.986),
 ('MTC','Meerut City Junction','Meerut','Uttar Pradesh','NR',28.978,77.674),
 ('MOZ','Muzaffarnagar','Muzaffarnagar','Uttar Pradesh','NR',29.471,77.708),
 ('SRE','Saharanpur Junction','Saharanpur','Uttar Pradesh','NR',29.964,77.543),
 ('RK','Roorkee','Roorkee','Uttarakhand','NR',29.85,77.883),
 ('HW','Haridwar Junction','Haridwar','Uttarakhand','NR',29.948,78.156),
 ('DDN','Dehradun','Dehradun','Uttarakhand','NR',30.314,78.033),
 ('PNP','Panipat Junction','Panipat','Haryana','NR',29.391,76.97),
 ('KKDE','Kurukshetra Junction','Kurukshetra','Haryana','NR',29.969,76.852),
 ('UMB','Ambala Cantt Junction','Ambala','Haryana','NR',30.338,76.827),
 ('CDG','Chandigarh','Chandigarh','Chandigarh','NR',30.702,76.821),
 ('KLK','Kalka','Kalka','Haryana','NR',30.838,76.932),
 ('RPJ','Rajpura Junction','Rajpura','Punjab','NR',30.477,76.594),
 ('LDH','Ludhiana Junction','Ludhiana','Punjab','NR',30.912,75.848),
 ('PGW','Phagwara Junction','Phagwara','Punjab','NR',31.218,75.771),
 ('JUC','Jalandhar City Junction','Jalandhar','Punjab','NR',31.326,75.591),
 ('BEAS','Beas Junction','Beas','Punjab','NR',31.516,75.289),
 ('ASR','Amritsar Junction','Amritsar','Punjab','NR',31.634,74.872),
 ('PTKC','Pathankot Cantt','Pathankot','Punjab','NR',32.269,75.642),
 ('KTHU','Kathua','Kathua','Jammu and Kashmir','NR',32.386,75.526),
 ('JAT','Jammu Tawi','Jammu','Jammu and Kashmir','NR',32.706,74.879),
 ('SVDK','Shri Mata Vaishno Devi Katra','Katra','Jammu and Kashmir','NR',32.976,74.924);

INSERT INTO rw_final_trains (number,name,category,source_code,destination_code) VALUES
 ('12951','Mumbai Central - New Delhi Tejas Rajdhani','RAJDHANI','MMCT','NDLS'),
 ('12952','New Delhi - Mumbai Central Tejas Rajdhani','RAJDHANI','NDLS','MMCT'),
 ('12955','Mumbai Central - Jaipur Superfast','SUPERFAST','MMCT','JP'),
 ('12956','Jaipur - Mumbai Central Superfast','SUPERFAST','JP','MMCT'),
 ('12004','New Delhi - Lucknow Swarn Shatabdi','SHATABDI','NDLS','LJN'),
 ('12003','Lucknow - New Delhi Swarn Shatabdi','SHATABDI','LJN','NDLS'),
 ('12029','New Delhi - Amritsar Swarn Shatabdi','SHATABDI','NDLS','ASR'),
 ('12030','Amritsar - New Delhi Swarn Shatabdi','SHATABDI','ASR','NDLS'),
 ('12017','New Delhi - Dehradun Shatabdi','SHATABDI','NDLS','DDN'),
 ('12018','Dehradun - New Delhi Shatabdi','SHATABDI','DDN','NDLS'),
 ('12009','Mumbai Central - Ahmedabad Shatabdi','SHATABDI','MMCT','ADI'),
 ('12010','Ahmedabad - Mumbai Central Shatabdi','SHATABDI','ADI','MMCT'),
 ('12123','Mumbai CSMT - Pune Deccan Queen','SUPERFAST','CSMT','PUNE'),
 ('12124','Pune - Mumbai CSMT Deccan Queen','SUPERFAST','PUNE','CSMT'),
 ('12005','New Delhi - Kalka Shatabdi','SHATABDI','NDLS','KLK'),
 ('12006','Kalka - New Delhi Shatabdi','SHATABDI','KLK','NDLS'),
 ('22436','New Delhi - Varanasi Vande Bharat','VANDE_BHARAT','NDLS','BSB'),
 ('22435','Varanasi - New Delhi Vande Bharat','VANDE_BHARAT','BSB','NDLS'),
 ('12425','New Delhi - Jammu Tawi Rajdhani','RAJDHANI','NDLS','JAT'),
 ('12426','Jammu Tawi - New Delhi Rajdhani','RAJDHANI','JAT','NDLS'),
 ('RW901','Nizamuddin Lucknow Express','EXPRESS','NZM','LJN'),
 ('RW902','Lucknow Varanasi Express','EXPRESS','LJN','BSB'),
 ('RW903','Lucknow Varanasi Intercity','EXPRESS','LJN','BSB'),
 ('RW904','Lucknow Varanasi Night Express','EXPRESS','LJN','BSB'),
 ('RW905','Bandra Sabarmati Express','EXPRESS','BDTS','SBT'),
 ('RW906','Nizamuddin Lucknow Night Express','EXPRESS','NZM','LJN'),
 ('RW907','Jammu Katra Express','EXPRESS','JAT','SVDK');

INSERT INTO rw_final_days (number,day) VALUES
 ('12951','MONDAY'),
 ('12951','TUESDAY'),
 ('12951','WEDNESDAY'),
 ('12951','THURSDAY'),
 ('12951','FRIDAY'),
 ('12951','SATURDAY'),
 ('12951','SUNDAY'),
 ('12952','MONDAY'),
 ('12952','TUESDAY'),
 ('12952','WEDNESDAY'),
 ('12952','THURSDAY'),
 ('12952','FRIDAY'),
 ('12952','SATURDAY'),
 ('12952','SUNDAY'),
 ('12955','MONDAY'),
 ('12955','TUESDAY'),
 ('12955','WEDNESDAY'),
 ('12955','THURSDAY'),
 ('12955','FRIDAY'),
 ('12955','SATURDAY'),
 ('12955','SUNDAY'),
 ('12956','MONDAY'),
 ('12956','TUESDAY'),
 ('12956','WEDNESDAY'),
 ('12956','THURSDAY'),
 ('12956','FRIDAY'),
 ('12956','SATURDAY'),
 ('12956','SUNDAY'),
 ('12004','MONDAY'),
 ('12004','TUESDAY'),
 ('12004','WEDNESDAY'),
 ('12004','THURSDAY'),
 ('12004','FRIDAY'),
 ('12004','SATURDAY'),
 ('12004','SUNDAY'),
 ('12003','MONDAY'),
 ('12003','TUESDAY'),
 ('12003','WEDNESDAY'),
 ('12003','THURSDAY'),
 ('12003','FRIDAY'),
 ('12003','SATURDAY'),
 ('12003','SUNDAY'),
 ('12029','MONDAY'),
 ('12029','TUESDAY'),
 ('12029','WEDNESDAY'),
 ('12029','FRIDAY'),
 ('12029','SATURDAY'),
 ('12029','SUNDAY'),
 ('12030','MONDAY'),
 ('12030','TUESDAY'),
 ('12030','WEDNESDAY'),
 ('12030','FRIDAY'),
 ('12030','SATURDAY'),
 ('12030','SUNDAY'),
 ('12017','MONDAY'),
 ('12017','TUESDAY'),
 ('12017','WEDNESDAY'),
 ('12017','THURSDAY'),
 ('12017','FRIDAY'),
 ('12017','SATURDAY'),
 ('12017','SUNDAY'),
 ('12018','MONDAY'),
 ('12018','TUESDAY'),
 ('12018','WEDNESDAY'),
 ('12018','THURSDAY'),
 ('12018','FRIDAY'),
 ('12018','SATURDAY'),
 ('12018','SUNDAY'),
 ('12009','MONDAY'),
 ('12009','TUESDAY'),
 ('12009','WEDNESDAY'),
 ('12009','THURSDAY'),
 ('12009','FRIDAY'),
 ('12009','SATURDAY'),
 ('12010','MONDAY'),
 ('12010','TUESDAY'),
 ('12010','WEDNESDAY'),
 ('12010','THURSDAY'),
 ('12010','FRIDAY'),
 ('12010','SATURDAY'),
 ('12123','MONDAY'),
 ('12123','TUESDAY'),
 ('12123','WEDNESDAY'),
 ('12123','THURSDAY'),
 ('12123','FRIDAY'),
 ('12123','SATURDAY'),
 ('12123','SUNDAY'),
 ('12124','MONDAY'),
 ('12124','TUESDAY'),
 ('12124','WEDNESDAY'),
 ('12124','THURSDAY'),
 ('12124','FRIDAY'),
 ('12124','SATURDAY'),
 ('12124','SUNDAY'),
 ('12005','MONDAY'),
 ('12005','TUESDAY'),
 ('12005','WEDNESDAY'),
 ('12005','THURSDAY'),
 ('12005','FRIDAY'),
 ('12005','SATURDAY'),
 ('12005','SUNDAY'),
 ('12006','MONDAY'),
 ('12006','TUESDAY'),
 ('12006','WEDNESDAY'),
 ('12006','THURSDAY'),
 ('12006','FRIDAY'),
 ('12006','SATURDAY'),
 ('12006','SUNDAY'),
 ('22436','MONDAY'),
 ('22436','TUESDAY'),
 ('22436','WEDNESDAY'),
 ('22436','FRIDAY'),
 ('22436','SATURDAY'),
 ('22436','SUNDAY'),
 ('22435','MONDAY'),
 ('22435','TUESDAY'),
 ('22435','WEDNESDAY'),
 ('22435','FRIDAY'),
 ('22435','SATURDAY'),
 ('22435','SUNDAY'),
 ('12425','MONDAY'),
 ('12425','TUESDAY'),
 ('12425','WEDNESDAY'),
 ('12425','THURSDAY'),
 ('12425','FRIDAY'),
 ('12425','SATURDAY'),
 ('12425','SUNDAY'),
 ('12426','MONDAY'),
 ('12426','TUESDAY'),
 ('12426','WEDNESDAY'),
 ('12426','THURSDAY'),
 ('12426','FRIDAY'),
 ('12426','SATURDAY'),
 ('12426','SUNDAY'),
 ('RW901','MONDAY'),
 ('RW901','TUESDAY'),
 ('RW901','WEDNESDAY'),
 ('RW901','THURSDAY'),
 ('RW901','FRIDAY'),
 ('RW901','SATURDAY'),
 ('RW901','SUNDAY'),
 ('RW902','MONDAY'),
 ('RW902','TUESDAY'),
 ('RW902','WEDNESDAY'),
 ('RW902','THURSDAY'),
 ('RW902','FRIDAY'),
 ('RW902','SATURDAY'),
 ('RW902','SUNDAY'),
 ('RW903','MONDAY'),
 ('RW903','TUESDAY'),
 ('RW903','WEDNESDAY'),
 ('RW903','THURSDAY'),
 ('RW903','FRIDAY'),
 ('RW903','SATURDAY'),
 ('RW903','SUNDAY'),
 ('RW904','MONDAY'),
 ('RW904','TUESDAY'),
 ('RW904','WEDNESDAY'),
 ('RW904','THURSDAY'),
 ('RW904','FRIDAY'),
 ('RW904','SATURDAY'),
 ('RW904','SUNDAY'),
 ('RW905','MONDAY'),
 ('RW905','TUESDAY'),
 ('RW905','WEDNESDAY'),
 ('RW905','THURSDAY'),
 ('RW905','FRIDAY'),
 ('RW905','SATURDAY'),
 ('RW905','SUNDAY'),
 ('RW906','MONDAY'),
 ('RW907','MONDAY'),
 ('RW907','TUESDAY'),
 ('RW907','WEDNESDAY'),
 ('RW907','THURSDAY'),
 ('RW907','FRIDAY'),
 ('RW907','SATURDAY'),
 ('RW907','SUNDAY');

INSERT INTO rw_final_classes (number,class,coaches,seats,base,rate,minimum) VALUES
 ('12951','CLASS_1A',1,24,250,2.8,500),
 ('12951','CLASS_2A',1,48,150,1.8,300),
 ('12951','CLASS_3A',2,72,100,1.2,200),
 ('12952','CLASS_1A',1,24,250,2.8,500),
 ('12952','CLASS_2A',1,48,150,1.8,300),
 ('12952','CLASS_3A',2,72,100,1.2,200),
 ('12955','SL',2,72,50,0.5,100),
 ('12955','CLASS_3A',2,72,100,1.2,200),
 ('12955','CLASS_2A',1,48,150,1.8,300),
 ('12955','CLASS_1A',1,24,250,2.8,500),
 ('12956','SL',2,72,50,0.5,100),
 ('12956','CLASS_3A',2,72,100,1.2,200),
 ('12956','CLASS_2A',1,48,150,1.8,300),
 ('12956','CLASS_1A',1,24,250,2.8,500),
 ('12004','EC',1,56,150,1.5,250),
 ('12003','EC',1,56,150,1.5,250),
 ('12029','EC',1,56,150,1.5,250),
 ('12030','EC',1,56,150,1.5,250),
 ('12017','EC',1,56,150,1.5,250),
 ('12018','EC',1,56,150,1.5,250),
 ('12009','EC',1,56,150,1.5,250),
 ('12010','EC',1,56,150,1.5,250),
 ('12123','CLASS_2S',2,80,20,0.3,50),
 ('12124','CLASS_2S',2,80,20,0.3,50),
 ('12005','EC',1,56,150,1.5,250),
 ('12006','EC',1,56,150,1.5,250),
 ('22436','EC',1,56,150,1.5,250),
 ('22435','EC',1,56,150,1.5,250),
 ('12425','CLASS_1A',1,24,250,2.8,500),
 ('12425','CLASS_2A',1,48,150,1.8,300),
 ('12425','CLASS_3A',2,72,100,1.2,200),
 ('12426','CLASS_1A',1,24,250,2.8,500),
 ('12426','CLASS_2A',1,48,150,1.8,300),
 ('12426','CLASS_3A',2,72,100,1.2,200),
 ('RW901','SL',1,8,50,0.5,100),
 ('RW901','CLASS_3A',1,8,100,1.2,200),
 ('RW901','CLASS_2A',1,6,150,1.8,300),
 ('RW901','CLASS_1A',1,4,250,2.8,500),
 ('RW901','CLASS_3E',1,8,80,1,180),
 ('RW901','EC',1,6,150,1.5,250),
 ('RW901','CLASS_2S',1,6,20,0.3,50),
 ('RW901','UR',1,6,10,0.2,30),
 ('RW902','SL',2,72,50,0.5,100),
 ('RW902','CLASS_3A',2,72,100,1.2,200),
 ('RW902','CLASS_2A',1,48,150,1.8,300),
 ('RW903','SL',2,72,50,0.5,100),
 ('RW903','CLASS_3A',2,72,100,1.2,200),
 ('RW903','CLASS_2A',1,48,150,1.8,300),
 ('RW904','SL',2,72,50,0.5,100),
 ('RW904','CLASS_3A',2,72,100,1.2,200),
 ('RW904','CLASS_2A',1,48,150,1.8,300),
 ('RW905','SL',2,72,50,0.5,100),
 ('RW905','CLASS_3A',2,72,100,1.2,200),
 ('RW905','CLASS_2A',1,48,150,1.8,300),
 ('RW906','SL',2,72,50,0.5,100),
 ('RW906','CLASS_3A',2,72,100,1.2,200),
 ('RW907','SL',1,2,50,0.5,100);


-- 12951 Mumbai Central - New Delhi Tejas Rajdhani
-- https://erail.in/train-enquiry/12951

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12951','MMCT',1,NULL,'17:00:00',1,1,0),
 ('12951','BVI',2,'17:20:00','17:22:00',1,1,30),
 ('12951','ST',3,'19:43:00','19:48:00',1,1,263),
 ('12951','BRC',4,'21:06:00','21:16:00',1,1,392),
 ('12951','RTM',5,'00:25:00','00:28:00',2,2,653),
 ('12951','NAD',6,'01:08:00','01:10:00',2,2,695),
 ('12951','KOTA',7,'03:15:00','03:20:00',2,2,920),
 ('12951','NDLS',8,'08:32:00',NULL,2,2,1384);


-- 12952 New Delhi - Mumbai Central Tejas Rajdhani
-- https://erail.in/train-enquiry/12952

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12952','NDLS',1,NULL,'16:55:00',1,1,0),
 ('12952','KOTA',2,'21:30:00','21:40:00',1,1,465),
 ('12952','NAD',3,'23:57:00','23:59:00',1,1,690),
 ('12952','RTM',4,'00:30:00','00:33:00',2,2,731),
 ('12952','BRC',5,'03:40:00','03:50:00',2,2,993),
 ('12952','ST',6,'05:13:00','05:18:00',2,2,1122),
 ('12952','BVI',7,'07:38:00','07:40:00',2,2,1355),
 ('12952','MMCT',8,'08:35:00',NULL,2,2,1384);


-- 12955 Mumbai Central - Jaipur Superfast
-- https://erail.in/train-enquiry/12955

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12955','MMCT',1,NULL,'19:05:00',1,1,0),
 ('12955','BVI',2,'19:32:00','19:34:00',1,1,30),
 ('12955','VAPI',3,'20:53:00','20:55:00',1,1,170),
 ('12955','ST',4,'22:30:00','22:35:00',1,1,263),
 ('12955','BRC',5,'00:08:00','00:18:00',2,2,392),
 ('12955','RTM',6,'04:05:00','04:10:00',2,2,653),
 ('12955','NAD',7,'04:58:00','05:00:00',2,2,695),
 ('12955','KOTA',8,'07:45:00','07:55:00',2,2,920),
 ('12955','SWM',9,'09:20:00','09:45:00',2,2,1027),
 ('12955','DPA',10,'11:27:00','11:30:00',2,2,1151),
 ('12955','JP',11,'12:00:00',NULL,2,2,1159);


-- 12956 Jaipur - Mumbai Central Superfast
-- https://erail.in/train-enquiry/12956

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12956','JP',1,NULL,'14:00:00',1,1,0),
 ('12956','DPA',2,'14:09:00','14:12:00',1,1,8),
 ('12956','SWM',3,'15:50:00','16:05:00',1,1,132),
 ('12956','KOTA',4,'17:10:00','17:20:00',1,1,240),
 ('12956','NAD',5,'20:35:00','20:37:00',1,1,465),
 ('12956','RTM',6,'21:10:00','21:20:00',1,1,506),
 ('12956','BRC',7,'00:56:00','01:06:00',2,2,767),
 ('12956','ST',8,'02:48:00','02:53:00',2,2,896),
 ('12956','VAPI',9,'03:58:00','04:00:00',2,2,989),
 ('12956','BVI',10,'05:47:00','05:50:00',2,2,1129),
 ('12956','MMCT',11,'06:55:00',NULL,2,2,1159);


-- 12004 New Delhi - Lucknow Swarn Shatabdi
-- https://erail.in/train-enquiry/12004

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12004','NDLS',1,NULL,'06:10:00',1,1,0),
 ('12004','GZB',2,'06:40:00','06:42:00',1,1,24),
 ('12004','ALJN',3,'07:47:00','07:49:00',1,1,129),
 ('12004','TDL',4,'08:45:00','08:47:00',1,1,207),
 ('12004','ETW',5,'09:40:00','09:42:00',1,1,299),
 ('12004','PHD',6,'10:14:00','10:15:00',1,1,355),
 ('12004','CNB',7,'11:23:00','11:28:00',1,1,439),
 ('12004','ON',8,'11:54:00','11:56:00',1,1,456),
 ('12004','LJN',9,'13:00:00',NULL,1,1,512);


-- 12003 Lucknow - New Delhi Swarn Shatabdi
-- https://erail.in/train-enquiry/12003

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12003','LJN',1,NULL,'15:30:00',1,1,0),
 ('12003','ON',2,'16:19:00','16:21:00',1,1,57),
 ('12003','CNB',3,'16:48:00','16:53:00',1,1,74),
 ('12003','PHD',4,'17:47:00','17:48:00',1,1,157),
 ('12003','ETW',5,'18:18:00','18:20:00',1,1,214),
 ('12003','TDL',6,'19:26:00','19:28:00',1,1,305),
 ('12003','ALJN',7,'20:15:00','20:17:00',1,1,383),
 ('12003','GZB',8,'21:38:00','21:40:00',1,1,489),
 ('12003','NDLS',9,'22:25:00',NULL,1,1,512);


-- 12029 New Delhi - Amritsar Swarn Shatabdi
-- https://erail.in/train-enquiry/12029

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12029','NDLS',1,NULL,'07:20:00',1,1,0),
 ('12029','UMB',2,'09:41:00','09:43:00',1,1,198),
 ('12029','RPJ',3,'10:02:00','10:04:00',1,1,226),
 ('12029','LDH',4,'11:08:00','11:11:00',1,1,312),
 ('12029','PGW',5,'11:40:00','11:42:00',1,1,348),
 ('12029','JUC',6,'12:06:00','12:08:00',1,1,369),
 ('12029','BEAS',7,'12:38:00','12:40:00',1,1,405),
 ('12029','ASR',8,'13:30:00',NULL,1,1,447);


-- 12030 Amritsar - New Delhi Swarn Shatabdi
-- https://erail.in/train-enquiry/12030

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12030','ASR',1,NULL,'16:50:00',1,1,0),
 ('12030','BEAS',2,'17:18:00','17:20:00',1,1,42),
 ('12030','JUC',3,'17:53:00','17:56:00',1,1,79),
 ('12030','PGW',4,'18:14:00','18:16:00',1,1,100),
 ('12030','LDH',5,'18:50:00','18:55:00',1,1,135),
 ('12030','RPJ',6,'19:52:00','19:54:00',1,1,221),
 ('12030','UMB',7,'20:26:00','20:30:00',1,1,249),
 ('12030','NDLS',8,'22:50:00',NULL,1,1,447);


-- 12017 New Delhi - Dehradun Shatabdi
-- https://erail.in/train-enquiry/12017

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12017','NDLS',1,NULL,'06:45:00',1,1,0),
 ('12017','GZB',2,'07:21:00','07:23:00',1,1,26),
 ('12017','MTC',3,'08:01:00','08:03:00',1,1,73),
 ('12017','MOZ',4,'08:41:00','08:43:00',1,1,129),
 ('12017','SRE',5,'09:50:00','10:15:00',1,1,187),
 ('12017','RK',6,'10:49:00','10:51:00',1,1,222),
 ('12017','HW',7,'11:35:00','11:40:00',1,1,263),
 ('12017','DDN',8,'12:55:00',NULL,1,1,315);


-- 12018 Dehradun - New Delhi Shatabdi
-- https://erail.in/train-enquiry/12018

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12018','DDN',1,NULL,'16:55:00',1,1,0),
 ('12018','HW',2,'18:02:00','18:07:00',1,1,52),
 ('12018','RK',3,'18:42:00','18:44:00',1,1,93),
 ('12018','SRE',4,'19:30:00','19:55:00',1,1,128),
 ('12018','MOZ',5,'20:38:00','20:40:00',1,1,186),
 ('12018','MTC',6,'21:18:00','21:20:00',1,1,242),
 ('12018','GZB',7,'22:02:00','22:04:00',1,1,289),
 ('12018','NDLS',8,'22:50:00',NULL,1,1,315);


-- 12009 Mumbai Central - Ahmedabad Shatabdi
-- https://erail.in/train-enquiry/12009

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12009','MMCT',1,NULL,'06:20:00',1,1,0),
 ('12009','BVI',2,'06:43:00','06:45:00',1,1,30),
 ('12009','VAPI',3,'08:05:00','08:07:00',1,1,170),
 ('12009','ST',4,'09:21:00','09:24:00',1,1,263),
 ('12009','BH',5,'10:02:00','10:04:00',1,1,322),
 ('12009','BRC',6,'10:54:00','10:59:00',1,1,392),
 ('12009','ANND',7,'11:30:00','11:32:00',1,1,427),
 ('12009','ND',8,'11:46:00','11:48:00',1,1,446),
 ('12009','ADI',9,'12:40:00',NULL,1,1,491);


-- 12010 Ahmedabad - Mumbai Central Shatabdi
-- https://erail.in/train-enquiry/12010

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12010','ADI',1,NULL,'15:10:00',1,1,0),
 ('12010','ND',2,'15:39:00','15:41:00',1,1,46),
 ('12010','ANND',3,'15:58:00','16:00:00',1,1,64),
 ('12010','BRC',4,'16:30:00','16:35:00',1,1,100),
 ('12010','BH',5,'17:22:00','17:24:00',1,1,170),
 ('12010','ST',6,'18:10:00','18:15:00',1,1,229),
 ('12010','VAPI',7,'19:18:00','19:20:00',1,1,322),
 ('12010','BVI',8,'20:48:00','20:50:00',1,1,462),
 ('12010','MMCT',9,'21:45:00',NULL,1,1,491);


-- 12123 Mumbai CSMT - Pune Deccan Queen
-- https://erail.in/train-enquiry/12123

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12123','CSMT',1,NULL,'17:10:00',1,1,0),
 ('12123','KJT',2,'18:33:00','18:35:00',1,1,100),
 ('12123','LNL',3,'19:18:00','19:20:00',1,1,128),
 ('12123','SVJR',4,'20:09:00','20:10:00',1,1,190),
 ('12123','PUNE',5,'20:25:00',NULL,1,1,192);


-- 12124 Pune - Mumbai CSMT Deccan Queen
-- https://erail.in/train-enquiry/12124

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12124','PUNE',1,NULL,'07:15:00',1,1,0),
 ('12124','LNL',2,'08:06:00','08:07:00',1,1,64),
 ('12124','DR',3,'10:03:00','10:05:00',1,1,183),
 ('12124','CSMT',4,'10:25:00',NULL,1,1,192);


-- 12005 New Delhi - Kalka Shatabdi
-- https://erail.in/train-enquiry/12005

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12005','NDLS',1,NULL,'17:15:00',1,1,0),
 ('12005','PNP',2,'18:18:00','18:20:00',1,1,89),
 ('12005','KKDE',3,'19:00:00','19:02:00',1,1,157),
 ('12005','UMB',4,'19:50:00','19:53:00',1,1,198),
 ('12005','CDG',5,'20:30:00','20:38:00',1,1,266),
 ('12005','KLK',6,'21:15:00',NULL,1,1,303);


-- 12006 Kalka - New Delhi Shatabdi
-- https://erail.in/train-enquiry/12006

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12006','KLK',1,NULL,'06:15:00',1,1,0),
 ('12006','CDG',2,'06:45:00','06:53:00',1,1,38),
 ('12006','UMB',3,'07:33:00','07:38:00',1,1,106),
 ('12006','KKDE',4,'08:08:00','08:10:00',1,1,147),
 ('12006','PNP',5,'08:50:00','08:52:00',1,1,214),
 ('12006','NDLS',6,'10:15:00',NULL,1,1,303);


-- 22436 New Delhi - Varanasi Vande Bharat
-- https://erail.in/train-enquiry/22436

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('22436','NDLS',1,NULL,'06:00:00',1,1,0),
 ('22436','CNB',2,'10:08:00','10:10:00',1,1,441),
 ('22436','PRYJ',3,'12:08:00','12:10:00',1,1,636),
 ('22436','BSB',4,'14:00:00',NULL,1,1,771);


-- 22435 Varanasi - New Delhi Vande Bharat
-- https://erail.in/train-enquiry/22435

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('22435','BSB',1,NULL,'15:00:00',1,1,0),
 ('22435','PRYJ',2,'16:30:00','16:32:00',1,1,135),
 ('22435','CNB',3,'18:30:00','18:32:00',1,1,330),
 ('22435','NDLS',4,'23:00:00',NULL,1,1,771);


-- 12425 New Delhi - Jammu Tawi Rajdhani
-- https://erail.in/train-enquiry/12425

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12425','NDLS',1,NULL,'20:40:00',1,1,0),
 ('12425','LDH',2,'00:28:00','00:38:00',2,2,312),
 ('12425','PTKC',3,'03:10:00','03:12:00',2,2,478),
 ('12425','KTHU',4,'03:41:00','03:43:00',2,2,501),
 ('12425','JAT',5,'05:00:00',NULL,2,2,577);


-- 12426 Jammu Tawi - New Delhi Rajdhani
-- https://erail.in/train-enquiry/12426

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12426','JAT',1,NULL,'21:25:00',1,1,0),
 ('12426','KTHU',2,'22:31:00','22:33:00',1,1,77),
 ('12426','PTKC',3,'23:05:00','23:07:00',1,1,100),
 ('12426','LDH',4,'01:35:00','01:45:00',2,2,265),
 ('12426','NDLS',5,'05:55:00',NULL,2,2,577);


-- RW901 Nizamuddin Lucknow Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW901','NZM',1,NULL,'06:00:00',1,1,0),
 ('RW901','AGC',2,'09:00:00','09:10:00',1,1,188),
 ('RW901','CNB',3,'13:00:00','13:10:00',1,1,470),
 ('RW901','LJN',4,'14:30:00',NULL,1,1,544);


-- RW902 Lucknow Varanasi Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW902','LJN',1,NULL,'16:00:00',1,1,0),
 ('RW902','PRYJ',2,'19:00:00','19:10:00',1,1,200),
 ('RW902','BSB',3,'21:00:00',NULL,1,1,335);


-- RW903 Lucknow Varanasi Intercity
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW903','LJN',1,NULL,'14:50:00',1,1,0),
 ('RW903','BSB',2,'20:00:00',NULL,1,1,335);


-- RW904 Lucknow Varanasi Night Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW904','LJN',1,NULL,'23:45:00',1,1,0),
 ('RW904','BSB',2,'05:00:00',NULL,2,2,335);


-- RW905 Bandra Sabarmati Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW905','BDTS',1,NULL,'08:00:00',1,1,0),
 ('RW905','ST',2,'11:00:00','11:10:00',1,1,252),
 ('RW905','BRC',3,'13:00:00','13:10:00',1,1,381),
 ('RW905','SBT',4,'15:00:00',NULL,1,1,490);


-- RW906 Nizamuddin Lucknow Night Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW906','NZM',1,NULL,'21:00:00',1,1,0),
 ('RW906','AGC',2,'23:55:00','00:05:00',1,2,188),
 ('RW906','LJN',3,'06:00:00',NULL,2,2,544);


-- RW907 Jammu Katra Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_final_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW907','JAT',1,NULL,'10:00:00',1,1,0),
 ('RW907','SVDK',2,'12:00:00',NULL,1,1,78);



DROP TEMPORARY TABLE IF EXISTS rw_major_stations;
DROP TEMPORARY TABLE IF EXISTS rw_major_trains;
DROP TEMPORARY TABLE IF EXISTS rw_major_stops;
DROP TEMPORARY TABLE IF EXISTS rw_major_new;
CREATE TEMPORARY TABLE rw_major_stations (
 code VARCHAR(10) PRIMARY KEY,name VARCHAR(255),city VARCHAR(255),state VARCHAR(255),zone VARCHAR(255),latitude DOUBLE,longitude DOUBLE);
CREATE TEMPORARY TABLE rw_major_trains (
 number VARCHAR(10) PRIMARY KEY,name VARCHAR(255),category VARCHAR(32),source_code VARCHAR(10),destination_code VARCHAR(10));
CREATE TEMPORARY TABLE rw_major_stops (
 number VARCHAR(10),code VARCHAR(10),sequence INT,arrival TIME,departure TIME,arrival_day INT,departure_day INT,km INT,
 PRIMARY KEY(number,sequence),UNIQUE(number,code));
CREATE TEMPORARY TABLE rw_major_new (number VARCHAR(10) PRIMARY KEY);
INSERT INTO rw_major_stations VALUES
 ('ADI','Ahmedabad Junction','Ahmedabad','Gujarat','WR',23.026,72.602),
 ('AGC','Agra Cantt','Agra','Uttar Pradesh','NCR',27.158,77.989),
 ('AII','Ajmer Junction','Ajmer','Rajasthan','NWR',26.456,74.638),
 ('AJJ','Arakkonam Junction','Arakkonam','Tamil Nadu','SR',13.077,79.671),
 ('AK','Akola Junction','Akola','Maharashtra','CR',20.722,77.004),
 ('ALJN','Aligarh Junction','Aligarh','Uttar Pradesh','NCR',27.888,78.074),
 ('ALLP','Alappuzha','Alappuzha','Kerala','SR',9.49,76.327),
 ('ANND','Anand Junction','Anand','Gujarat','WR',22.563,72.966),
 ('ANVT','Anand Vihar Terminal','Delhi','Delhi','NR',28.65,77.315),
 ('ASN','Asansol Junction','Asansol','West Bengal','ER',23.691,86.974),
 ('ASR','Amritsar Junction','Amritsar','Punjab','NR',31.634,74.872),
 ('BAM','Brahmapur','Brahmapur','Odisha','ECoR',19.298,84.805),
 ('BBS','Bhubaneswar','Bhubaneswar','Odisha','ECoR',20.266,85.843),
 ('BDTS','Bandra Terminus','Mumbai','Maharashtra','WR',19.062,72.84),
 ('BEAS','Beas Junction','Beas','Punjab','NR',31.516,75.289),
 ('BHC','Bhadrak','Bhadrak','Odisha','ECoR',21.058,86.497),
 ('BKSC','Bokaro Steel City','Bokaro','Jharkhand','SER',23.663,86.151),
 ('BPL','Bhopal Junction','Bhopal','Madhya Pradesh','WCR',23.268,77.413),
 ('BRC','Vadodara Junction','Vadodara','Gujarat','WR',22.31,73.181),
 ('BSB','Varanasi Junction','Varanasi','Uttar Pradesh','NR',25.327,82.986),
 ('BSL','Bhusaval Junction','Bhusaval','Maharashtra','CR',21.046,75.789),
 ('BSP','Bilaspur Junction','Bilaspur','Chhattisgarh','SECR',22.057,82.172),
 ('BVI','Borivali','Mumbai','Maharashtra','WR',19.23,72.857),
 ('BZA','Vijayawada Junction','Vijayawada','Andhra Pradesh','SCR',16.519,80.619),
 ('CAN','Kannur','Kannur','Kerala','SR',11.872,75.369),
 ('CBE','Coimbatore Junction','Coimbatore','Tamil Nadu','SR',10.995,76.967),
 ('CDG','Chandigarh','Chandigarh','Chandigarh','NR',30.702,76.821),
 ('CLT','Kozhikode','Kozhikode','Kerala','SR',11.245,75.781),
 ('CNB','Kanpur Central','Kanpur','Uttar Pradesh','NCR',26.454,80.35),
 ('CSMT','Chhatrapati Shivaji Maharaj Terminus','Mumbai','Maharashtra','CR',18.93985,72.83544),
 ('CTC','Cuttack Junction','Cuttack','Odisha','ECoR',20.465,85.9),
 ('DDU','Pt Deen Dayal Upadhyaya Junction','Chandauli','Uttar Pradesh','ECR',25.282,83.12),
 ('DHN','Dhanbad Junction','Dhanbad','Jharkhand','ECR',23.791,86.429),
 ('DLI','Delhi Junction','Delhi','Delhi','NR',28.661,77.228),
 ('DMM','Dharmavaram Junction','Dharmavaram','Andhra Pradesh','SCR',14.413,77.722),
 ('DURG','Durg Junction','Durg','Chhattisgarh','SECR',21.2,81.29),
 ('ED','Erode Junction','Erode','Tamil Nadu','SR',11.328,77.727),
 ('ERS','Ernakulam Junction','Kochi','Kerala','SR',9.97,76.291),
 ('ET','Itarsi Junction','Itarsi','Madhya Pradesh','WCR',22.612,77.758),
 ('GAYA','Gaya Junction','Gaya','Bihar','ECR',24.807,85.0),
 ('GHY','Guwahati','Guwahati','Assam','NFR',26.182,91.751),
 ('GTL','Guntakal Junction','Guntakal','Andhra Pradesh','SCR',15.172,77.365),
 ('GZB','Ghaziabad Junction','Ghaziabad','Uttar Pradesh','NR',28.654,77.427),
 ('HWH','Howrah Junction','Kolkata','West Bengal','ER',22.584,88.343),
 ('HYB','Hyderabad Deccan','Hyderabad','Telangana','SCR',17.392,78.467),
 ('INDB','Indore Junction','Indore','Madhya Pradesh','WR',22.717,75.869),
 ('JAT','Jammu Tawi','Jammu','Jammu and Kashmir','NR',32.706,74.879),
 ('JP','Jaipur Junction','Jaipur','Rajasthan','NWR',26.92,75.787),
 ('JTJ','Jolarpettai Junction','Jolarpettai','Tamil Nadu','SR',12.571,78.571),
 ('JU','Jodhpur Junction','Jodhpur','Rajasthan','NWR',26.283,73.022),
 ('JUC','Jalandhar City Junction','Jalandhar','Punjab','NR',31.326,75.591),
 ('KGP','Kharagpur Junction','Kharagpur','West Bengal','SER',22.34,87.325),
 ('KJM','Krishnarajapuram','Bengaluru','Karnataka','SWR',13.001,77.675),
 ('KJT','Karjat Junction','Karjat','Maharashtra','CR',18.91,73.32),
 ('KKDE','Kurukshetra Junction','Kurukshetra','Haryana','NR',29.969,76.852),
 ('KOTA','Kota Junction','Kota','Rajasthan','WCR',25.224,75.88),
 ('KPD','Katpadi Junction','Vellore','Tamil Nadu','SR',12.972,79.138),
 ('KTHU','Kathua','Kathua','Jammu and Kashmir','NR',32.386,75.526),
 ('KYN','Kalyan Junction','Kalyan','Maharashtra','CR',19.236,73.13),
 ('KZJ','Kazipet Junction','Warangal','Telangana','SCR',17.974,79.506),
 ('LDH','Ludhiana Junction','Ludhiana','Punjab','NR',30.912,75.848),
 ('LJN','Lucknow Junction','Lucknow','Uttar Pradesh','NER',26.831,80.922),
 ('LNL','Lonavala','Lonavala','Maharashtra','CR',18.75,73.407),
 ('MAJN','Mangaluru Junction','Mangaluru','Karnataka','SR',12.867,74.879),
 ('MAO','Madgaon Junction','Margao','Goa','KR',15.267,73.97),
 ('MAS','MGR Chennai Central','Chennai','Tamil Nadu','SR',13.083,80.275),
 ('MLDT','Malda Town','Malda','West Bengal','ER',25.01,88.14),
 ('MMCT','Mumbai Central','Mumbai','Maharashtra','WR',18.9697,72.8194),
 ('MS','Chennai Egmore','Chennai','Tamil Nadu','SR',13.079,80.261),
 ('NBQ','New Bongaigaon Junction','Bongaigaon','Assam','NFR',26.478,90.558),
 ('NDLS','New Delhi','New Delhi','Delhi','NR',28.6428,77.21908),
 ('NGP','Nagpur Junction','Nagpur','Maharashtra','CR',21.153,79.088),
 ('NJP','New Jalpaiguri Junction','Siliguri','West Bengal','NFR',26.683,88.444),
 ('NLR','Nellore','Nellore','Andhra Pradesh','SCR',14.453,79.988),
 ('NZM','Hazrat Nizamuddin','New Delhi','Delhi','NR',28.58917,77.25417),
 ('OGL','Ongole','Ongole','Andhra Pradesh','SCR',15.497,80.06),
 ('PGT','Palakkad Junction','Palakkad','Kerala','SR',10.799,76.643),
 ('PNBE','Patna Junction','Patna','Bihar','ECR',25.603,85.137),
 ('PNP','Panipat Junction','Panipat','Haryana','NR',29.391,76.97),
 ('PNVL','Panvel Junction','Navi Mumbai','Maharashtra','CR',18.99,73.121),
 ('PRYJ','Prayagraj Junction','Prayagraj','Uttar Pradesh','NCR',25.445,81.824),
 ('PTKC','Pathankot Cantt','Pathankot','Punjab','NR',32.269,75.642),
 ('PUNE','Pune Junction','Pune','Maharashtra','CR',18.528,73.874),
 ('QLN','Kollam Junction','Kollam','Kerala','SR',8.886,76.596),
 ('R','Raipur Junction','Raipur','Chhattisgarh','SECR',21.251,81.63),
 ('RC','Raichur','Raichur','Karnataka','SCR',16.206,77.347),
 ('RJY','Rajahmundry','Rajahmundry','Andhra Pradesh','SCR',16.983,81.784),
 ('RN','Ratnagiri','Ratnagiri','Maharashtra','KR',17.0,73.337),
 ('RNC','Ranchi','Ranchi','Jharkhand','SER',23.35,85.336),
 ('ROU','Rourkela Junction','Rourkela','Odisha','SER',22.227,84.863),
 ('RTM','Ratlam Junction','Ratlam','Madhya Pradesh','WR',23.331,75.05),
 ('RU','Renigunta Junction','Tirupati','Andhra Pradesh','SCR',13.635,79.503),
 ('SA','Salem Junction','Salem','Tamil Nadu','SR',11.671,78.114),
 ('SBC','KSR Bengaluru','Bengaluru','Karnataka','SWR',12.979,77.565),
 ('SBT','Sabarmati Junction','Ahmedabad','Gujarat','WR',23.072,72.588),
 ('SC','Secunderabad Junction','Hyderabad','Telangana','SCR',17.434,78.502),
 ('SDAH','Sealdah','Kolkata','West Bengal','ER',22.567,88.37),
 ('SRR','Shoranur Junction','Shoranur','Kerala','SR',10.758,76.272),
 ('ST','Surat','Surat','Gujarat','WR',21.204,72.841),
 ('SUR','Solapur','Solapur','Maharashtra','CR',17.665,75.892),
 ('SWM','Sawai Madhopur Junction','Sawai Madhopur','Rajasthan','WCR',26.018,76.359),
 ('TATA','Tatanagar Junction','Jamshedpur','Jharkhand','SER',22.769,86.198),
 ('TCR','Thrissur','Thrissur','Kerala','SR',10.514,76.208),
 ('TVC','Thiruvananthapuram Central','Thiruvananthapuram','Kerala','SR',8.487,76.952),
 ('UJN','Ujjain Junction','Ujjain','Madhya Pradesh','WR',23.179,75.78),
 ('UMB','Ambala Cantt Junction','Ambala','Haryana','NR',30.338,76.827),
 ('VGLJ','Virangana Lakshmibai Jhansi Junction','Jhansi','Uttar Pradesh','NCR',25.444,78.552),
 ('VKB','Vikarabad Junction','Vikarabad','Telangana','SCR',17.336,77.905),
 ('VSKP','Visakhapatnam','Visakhapatnam','Andhra Pradesh','ECoR',17.722,83.289),
 ('WADI','Wadi Junction','Wadi','Karnataka','SCR',17.054,76.991),
 ('YPR','Yesvantpur Junction','Bengaluru','Karnataka','SWR',13.023,77.551);

INSERT INTO rw_major_trains VALUES
 ('RW1001','Mumbai Central - New Delhi Express','EXPRESS','MMCT','NDLS'),
 ('RW1002','New Delhi - Mumbai Central Express','EXPRESS','NDLS','MMCT'),
 ('RW1003','Mumbai CSMT - New Delhi Express','EXPRESS','CSMT','NDLS'),
 ('RW1004','New Delhi - Mumbai CSMT Express','EXPRESS','NDLS','CSMT'),
 ('RW1005','Mumbai - Pune Express','EXPRESS','CSMT','PUNE'),
 ('RW1006','Pune - Mumbai Express','EXPRESS','PUNE','CSMT'),
 ('RW1007','Mumbai - Bengaluru Express','EXPRESS','CSMT','SBC'),
 ('RW1008','Bengaluru - Mumbai Express','EXPRESS','SBC','CSMT'),
 ('RW1009','Mumbai - Chennai Express','EXPRESS','CSMT','MAS'),
 ('RW1010','Chennai - Mumbai Express','EXPRESS','MAS','CSMT'),
 ('RW1011','Mumbai - Hyderabad Express','EXPRESS','CSMT','SC'),
 ('RW1012','Hyderabad - Mumbai Express','EXPRESS','SC','CSMT'),
 ('RW1013','Mumbai - Kolkata Express','EXPRESS','CSMT','HWH'),
 ('RW1014','Kolkata - Mumbai Express','EXPRESS','HWH','CSMT'),
 ('RW1015','Mumbai Central - Ahmedabad Express','EXPRESS','MMCT','ADI'),
 ('RW1016','Ahmedabad - Mumbai Central Express','EXPRESS','ADI','MMCT'),
 ('RW1017','Mumbai Central - Jaipur Express','EXPRESS','MMCT','JP'),
 ('RW1018','Jaipur - Mumbai Central Express','EXPRESS','JP','MMCT'),
 ('RW1019','Mumbai - Goa Express','EXPRESS','CSMT','MAO'),
 ('RW1020','Goa - Mumbai Express','EXPRESS','MAO','CSMT'),
 ('RW1021','New Delhi - Kolkata Express','EXPRESS','NDLS','HWH'),
 ('RW1022','Kolkata - New Delhi Express','EXPRESS','HWH','NDLS'),
 ('RW1023','New Delhi - Chennai Express','EXPRESS','NDLS','MAS'),
 ('RW1024','Chennai - New Delhi Express','EXPRESS','MAS','NDLS'),
 ('RW1025','New Delhi - Bengaluru Express','EXPRESS','NDLS','SBC'),
 ('RW1026','Bengaluru - New Delhi Express','EXPRESS','SBC','NDLS'),
 ('RW1027','New Delhi - Patna Express','EXPRESS','NDLS','PNBE'),
 ('RW1028','Patna - New Delhi Express','EXPRESS','PNBE','NDLS'),
 ('RW1029','New Delhi - Lucknow Express','EXPRESS','NDLS','LJN'),
 ('RW1030','Lucknow - New Delhi Express','EXPRESS','LJN','NDLS'),
 ('RW1031','Lucknow - Varanasi Express','EXPRESS','LJN','BSB'),
 ('RW1032','Varanasi - Lucknow Express','EXPRESS','BSB','LJN'),
 ('RW1033','New Delhi - Chandigarh Express','EXPRESS','NDLS','CDG'),
 ('RW1034','Chandigarh - New Delhi Express','EXPRESS','CDG','NDLS'),
 ('RW1035','New Delhi - Amritsar Express','EXPRESS','NDLS','ASR'),
 ('RW1036','Amritsar - New Delhi Express','EXPRESS','ASR','NDLS'),
 ('RW1037','New Delhi - Jammu Express','EXPRESS','NDLS','JAT'),
 ('RW1038','Jammu - New Delhi Express','EXPRESS','JAT','NDLS'),
 ('RW1039','Ahmedabad - Jaipur Express','EXPRESS','ADI','JP'),
 ('RW1040','Jaipur - Ahmedabad Express','EXPRESS','JP','ADI'),
 ('RW1041','Jaipur - Jodhpur Express','EXPRESS','JP','JU'),
 ('RW1042','Jodhpur - Jaipur Express','EXPRESS','JU','JP'),
 ('RW1043','Mumbai Central - Indore Express','EXPRESS','MMCT','INDB'),
 ('RW1044','Indore - Mumbai Central Express','EXPRESS','INDB','MMCT'),
 ('RW1045','Bengaluru - Chennai Express','EXPRESS','SBC','MAS'),
 ('RW1046','Chennai - Bengaluru Express','EXPRESS','MAS','SBC'),
 ('RW1047','Hyderabad - Chennai Express','EXPRESS','SC','MAS'),
 ('RW1048','Chennai - Hyderabad Express','EXPRESS','MAS','SC'),
 ('RW1049','Bengaluru - Kochi Express','EXPRESS','SBC','ERS'),
 ('RW1050','Kochi - Bengaluru Express','EXPRESS','ERS','SBC'),
 ('RW1051','Kochi - Thiruvananthapuram Express','EXPRESS','ERS','TVC'),
 ('RW1052','Thiruvananthapuram - Kochi Express','EXPRESS','TVC','ERS'),
 ('RW1053','Goa - Kochi Express','EXPRESS','MAO','ERS'),
 ('RW1054','Kochi - Goa Express','EXPRESS','ERS','MAO'),
 ('RW1055','Chennai - Bhubaneswar Express','EXPRESS','MAS','BBS'),
 ('RW1056','Bhubaneswar - Chennai Express','EXPRESS','BBS','MAS'),
 ('RW1057','Bhubaneswar - Kolkata Express','EXPRESS','BBS','HWH'),
 ('RW1058','Kolkata - Bhubaneswar Express','EXPRESS','HWH','BBS'),
 ('RW1059','Kolkata - Guwahati Express','EXPRESS','HWH','GHY'),
 ('RW1060','Guwahati - Kolkata Express','EXPRESS','GHY','HWH'),
 ('RW1061','Kolkata - Ranchi Express','EXPRESS','HWH','RNC'),
 ('RW1062','Ranchi - Kolkata Express','EXPRESS','RNC','HWH'),
 ('RW1063','Patna - Ranchi Express','EXPRESS','PNBE','RNC'),
 ('RW1064','Ranchi - Patna Express','EXPRESS','RNC','PNBE'),
 ('RW1065','Varanasi - Patna Express','EXPRESS','BSB','PNBE'),
 ('RW1066','Patna - Varanasi Express','EXPRESS','PNBE','BSB'),
 ('RW1067','Bhopal - Indore Express','EXPRESS','BPL','INDB'),
 ('RW1068','Indore - Bhopal Express','EXPRESS','INDB','BPL');

INSERT INTO rw_major_stops VALUES
 ('RW1001','MMCT',1,NULL,'16:00:00',1,1,0),
 ('RW1001','BVI',2,'16:35:00','16:45:00',1,1,30),
 ('RW1001','ST',3,'21:00:00','21:10:00',1,1,263),
 ('RW1001','BRC',4,'23:35:00','23:45:00',1,1,392),
 ('RW1001','RTM',5,'04:30:00','04:40:00',2,2,650),
 ('RW1001','KOTA',6,'09:35:00','09:45:00',2,2,917),
 ('RW1001','NDLS',7,'18:15:00',NULL,2,2,1384),
 ('RW1002','NDLS',1,NULL,'16:00:00',1,1,0),
 ('RW1002','KOTA',2,'00:30:00','00:40:00',2,2,467),
 ('RW1002','RTM',3,'05:35:00','05:45:00',2,2,734),
 ('RW1002','BRC',4,'10:30:00','10:40:00',2,2,992),
 ('RW1002','ST',5,'13:05:00','13:15:00',2,2,1121),
 ('RW1002','BVI',6,'17:30:00','17:40:00',2,2,1354),
 ('RW1002','MMCT',7,'18:15:00',NULL,2,2,1384),
 ('RW1003','CSMT',1,NULL,'18:00:00',1,1,0),
 ('RW1003','KYN',2,'19:00:00','19:10:00',1,1,54),
 ('RW1003','BSL',3,'02:20:00','02:30:00',2,2,445),
 ('RW1003','BPL',4,'09:40:00','09:50:00',2,2,838),
 ('RW1003','VGLJ',5,'15:10:00','15:20:00',2,2,1129),
 ('RW1003','AGC',6,'19:15:00','19:25:00',2,2,1344),
 ('RW1003','NZM',7,'22:55:00','23:05:00',2,2,1532),
 ('RW1003','NDLS',8,'23:15:00',NULL,2,2,1540),
 ('RW1004','NDLS',1,NULL,'18:00:00',1,1,0),
 ('RW1004','NZM',2,'18:10:00','18:20:00',1,1,8),
 ('RW1004','AGC',3,'21:50:00','22:00:00',1,1,196),
 ('RW1004','VGLJ',4,'01:55:00','02:05:00',2,2,411),
 ('RW1004','BPL',5,'07:25:00','07:35:00',2,2,702),
 ('RW1004','BSL',6,'14:45:00','14:55:00',2,2,1095),
 ('RW1004','KYN',7,'22:05:00','22:15:00',2,2,1486),
 ('RW1004','CSMT',8,'23:15:00',NULL,2,2,1540),
 ('RW1005','CSMT',1,NULL,'06:00:00',1,1,0),
 ('RW1005','KYN',2,'07:00:00','07:10:00',1,1,54),
 ('RW1005','KJT',3,'08:05:00','08:15:00',1,1,100),
 ('RW1005','LNL',4,'08:50:00','09:00:00',1,1,128),
 ('RW1005','PUNE',5,'10:10:00',NULL,1,1,192),
 ('RW1006','PUNE',1,NULL,'17:00:00',1,1,0),
 ('RW1006','LNL',2,'18:10:00','18:20:00',1,1,64),
 ('RW1006','KJT',3,'18:55:00','19:05:00',1,1,92),
 ('RW1006','KYN',4,'20:00:00','20:10:00',1,1,138),
 ('RW1006','CSMT',5,'21:10:00',NULL,1,1,192),
 ('RW1007','CSMT',1,NULL,'08:00:00',1,1,0),
 ('RW1007','PUNE',2,'11:30:00','11:40:00',1,1,192),
 ('RW1007','SUR',3,'16:30:00','16:40:00',1,1,456),
 ('RW1007','WADI',4,'19:25:00','19:35:00',1,1,605),
 ('RW1007','RC',5,'21:35:00','21:45:00',1,1,713),
 ('RW1007','GTL',6,'00:00:00','00:10:00',2,2,834),
 ('RW1007','DMM',7,'02:05:00','02:15:00',2,2,936),
 ('RW1007','SBC',8,'05:40:00',NULL,2,2,1120),
 ('RW1008','SBC',1,NULL,'08:00:00',1,1,0),
 ('RW1008','DMM',2,'11:25:00','11:35:00',1,1,184),
 ('RW1008','GTL',3,'13:30:00','13:40:00',1,1,286),
 ('RW1008','RC',4,'15:55:00','16:05:00',1,1,407),
 ('RW1008','WADI',5,'18:05:00','18:15:00',1,1,515),
 ('RW1008','SUR',6,'21:00:00','21:10:00',1,1,664),
 ('RW1008','PUNE',7,'02:00:00','02:10:00',2,2,928),
 ('RW1008','CSMT',8,'05:40:00',NULL,2,2,1120),
 ('RW1009','CSMT',1,NULL,'12:00:00',1,1,0),
 ('RW1009','PUNE',2,'15:30:00','15:40:00',1,1,192),
 ('RW1009','SUR',3,'20:30:00','20:40:00',1,1,456),
 ('RW1009','WADI',4,'23:25:00','23:35:00',1,1,605),
 ('RW1009','RC',5,'01:35:00','01:45:00',2,2,713),
 ('RW1009','GTL',6,'04:00:00','04:10:00',2,2,834),
 ('RW1009','RU',7,'09:50:00','10:00:00',2,2,1143),
 ('RW1009','AJJ',8,'11:25:00','11:35:00',2,2,1220),
 ('RW1009','MAS',9,'12:55:00',NULL,2,2,1289),
 ('RW1010','MAS',1,NULL,'12:00:00',1,1,0),
 ('RW1010','AJJ',2,'13:20:00','13:30:00',1,1,69),
 ('RW1010','RU',3,'14:55:00','15:05:00',1,1,146),
 ('RW1010','GTL',4,'20:45:00','20:55:00',1,1,455),
 ('RW1010','RC',5,'23:10:00','23:20:00',1,1,576),
 ('RW1010','WADI',6,'01:20:00','01:30:00',2,2,684),
 ('RW1010','SUR',7,'04:15:00','04:25:00',2,2,833),
 ('RW1010','PUNE',8,'09:15:00','09:25:00',2,2,1097),
 ('RW1010','CSMT',9,'12:55:00',NULL,2,2,1289),
 ('RW1011','CSMT',1,NULL,'20:00:00',1,1,0),
 ('RW1011','PUNE',2,'23:30:00','23:40:00',1,1,192),
 ('RW1011','SUR',3,'04:30:00','04:40:00',2,2,456),
 ('RW1011','WADI',4,'07:25:00','07:35:00',2,2,605),
 ('RW1011','VKB',5,'09:40:00','09:50:00',2,2,718),
 ('RW1011','SC',6,'11:10:00',NULL,2,2,790),
 ('RW1012','SC',1,NULL,'20:00:00',1,1,0),
 ('RW1012','VKB',2,'21:20:00','21:30:00',1,1,72),
 ('RW1012','WADI',3,'23:35:00','23:45:00',1,1,185),
 ('RW1012','SUR',4,'02:30:00','02:40:00',2,2,334),
 ('RW1012','PUNE',5,'07:30:00','07:40:00',2,2,598),
 ('RW1012','CSMT',6,'11:10:00',NULL,2,2,790),
 ('RW1013','CSMT',1,NULL,'06:00:00',1,1,0),
 ('RW1013','BSL',2,'14:10:00','14:20:00',1,1,445),
 ('RW1013','AK',3,'16:55:00','17:05:00',1,1,585),
 ('RW1013','NGP',4,'21:40:00','21:50:00',1,1,837),
 ('RW1013','DURG',5,'02:40:00','02:50:00',2,2,1099),
 ('RW1013','R',6,'03:35:00','03:45:00',2,2,1136),
 ('RW1013','BSP',7,'05:50:00','06:00:00',2,2,1247),
 ('RW1013','ROU',8,'11:35:00','11:45:00',2,2,1550),
 ('RW1013','TATA',9,'14:45:00','14:55:00',2,2,1715),
 ('RW1013','KGP',10,'17:25:00','17:35:00',2,2,1849),
 ('RW1013','HWH',11,'19:45:00',NULL,2,2,1964),
 ('RW1014','HWH',1,NULL,'06:00:00',1,1,0),
 ('RW1014','KGP',2,'08:10:00','08:20:00',1,1,115),
 ('RW1014','TATA',3,'10:50:00','11:00:00',1,1,249),
 ('RW1014','ROU',4,'14:00:00','14:10:00',1,1,414),
 ('RW1014','BSP',5,'19:45:00','19:55:00',1,1,717),
 ('RW1014','R',6,'22:00:00','22:10:00',1,1,828),
 ('RW1014','DURG',7,'22:55:00','23:05:00',1,1,865),
 ('RW1014','NGP',8,'03:55:00','04:05:00',2,2,1127),
 ('RW1014','AK',9,'08:40:00','08:50:00',2,2,1379),
 ('RW1014','BSL',10,'11:25:00','11:35:00',2,2,1519),
 ('RW1014','CSMT',11,'19:45:00',NULL,2,2,1964),
 ('RW1015','MMCT',1,NULL,'07:00:00',1,1,0),
 ('RW1015','BVI',2,'07:35:00','07:45:00',1,1,30),
 ('RW1015','ST',3,'12:00:00','12:10:00',1,1,263),
 ('RW1015','BRC',4,'14:35:00','14:45:00',1,1,392),
 ('RW1015','ANND',5,'15:25:00','15:35:00',1,1,427),
 ('RW1015','ADI',6,'16:45:00',NULL,1,1,491),
 ('RW1016','ADI',1,NULL,'08:00:00',1,1,0),
 ('RW1016','ANND',2,'09:10:00','09:20:00',1,1,64),
 ('RW1016','BRC',3,'10:00:00','10:10:00',1,1,99),
 ('RW1016','ST',4,'12:35:00','12:45:00',1,1,228),
 ('RW1016','BVI',5,'17:00:00','17:10:00',1,1,461),
 ('RW1016','MMCT',6,'17:45:00',NULL,1,1,491),
 ('RW1017','MMCT',1,NULL,'15:00:00',1,1,0),
 ('RW1017','ST',2,'19:50:00','20:00:00',1,1,263),
 ('RW1017','BRC',3,'22:25:00','22:35:00',1,1,392),
 ('RW1017','RTM',4,'03:20:00','03:30:00',2,2,650),
 ('RW1017','KOTA',5,'08:25:00','08:35:00',2,2,917),
 ('RW1017','SWM',6,'10:35:00','10:45:00',2,2,1025),
 ('RW1017','JP',7,'13:10:00',NULL,2,2,1156),
 ('RW1018','JP',1,NULL,'15:00:00',1,1,0),
 ('RW1018','SWM',2,'17:25:00','17:35:00',1,1,131),
 ('RW1018','KOTA',3,'19:35:00','19:45:00',1,1,239),
 ('RW1018','RTM',4,'00:40:00','00:50:00',2,2,506),
 ('RW1018','BRC',5,'05:35:00','05:45:00',2,2,764),
 ('RW1018','ST',6,'08:10:00','08:20:00',2,2,893),
 ('RW1018','MMCT',7,'13:10:00',NULL,2,2,1156),
 ('RW1019','CSMT',1,NULL,'06:00:00',1,1,0),
 ('RW1019','PNVL',2,'07:20:00','07:30:00',1,1,69),
 ('RW1019','RN',3,'12:50:00','13:00:00',1,1,362),
 ('RW1019','MAO',4,'17:00:00',NULL,1,1,581),
 ('RW1020','MAO',1,NULL,'09:00:00',1,1,0),
 ('RW1020','RN',2,'13:00:00','13:10:00',1,1,219),
 ('RW1020','PNVL',3,'18:30:00','18:40:00',1,1,512),
 ('RW1020','CSMT',4,'20:00:00',NULL,1,1,581),
 ('RW1021','NDLS',1,NULL,'09:00:00',1,1,0),
 ('RW1021','CNB',2,'17:05:00','17:15:00',1,1,441),
 ('RW1021','PRYJ',3,'20:50:00','21:00:00',1,1,636),
 ('RW1021','DDU',4,'23:50:00','00:00:00',1,2,788),
 ('RW1021','GAYA',5,'03:45:00','03:55:00',2,2,993),
 ('RW1021','DHN',6,'07:35:00','07:45:00',2,2,1192),
 ('RW1021','ASN',7,'08:50:00','09:00:00',2,2,1250),
 ('RW1021','HWH',8,'12:40:00',NULL,2,2,1450),
 ('RW1022','HWH',1,NULL,'09:00:00',1,1,0),
 ('RW1022','ASN',2,'12:40:00','12:50:00',1,1,200),
 ('RW1022','DHN',3,'13:55:00','14:05:00',1,1,258),
 ('RW1022','GAYA',4,'17:45:00','17:55:00',1,1,457),
 ('RW1022','DDU',5,'21:40:00','21:50:00',1,1,662),
 ('RW1022','PRYJ',6,'00:40:00','00:50:00',2,2,814),
 ('RW1022','CNB',7,'04:25:00','04:35:00',2,2,1009),
 ('RW1022','NDLS',8,'12:40:00',NULL,2,2,1450),
 ('RW1023','NDLS',1,NULL,'15:00:00',1,1,0),
 ('RW1023','AGC',2,'18:35:00','18:45:00',1,1,195),
 ('RW1023','VGLJ',3,'22:40:00','22:50:00',1,1,410),
 ('RW1023','BPL',4,'04:10:00','04:20:00',2,2,701),
 ('RW1023','ET',5,'06:05:00','06:15:00',2,2,793),
 ('RW1023','NGP',6,'11:45:00','11:55:00',2,2,1091),
 ('RW1023','KZJ',7,'20:15:00','20:25:00',2,2,1545),
 ('RW1023','BZA',8,'00:25:00','00:35:00',3,3,1764),
 ('RW1023','NLR',9,'05:15:00','05:25:00',3,3,2020),
 ('RW1023','MAS',10,'08:40:00',NULL,3,3,2195),
 ('RW1024','MAS',1,NULL,'15:00:00',1,1,0),
 ('RW1024','NLR',2,'18:15:00','18:25:00',1,1,175),
 ('RW1024','BZA',3,'23:05:00','23:15:00',1,1,431),
 ('RW1024','KZJ',4,'03:15:00','03:25:00',2,2,650),
 ('RW1024','NGP',5,'11:45:00','11:55:00',2,2,1104),
 ('RW1024','ET',6,'17:25:00','17:35:00',2,2,1402),
 ('RW1024','BPL',7,'19:20:00','19:30:00',2,2,1494),
 ('RW1024','VGLJ',8,'00:50:00','01:00:00',3,3,1785),
 ('RW1024','AGC',9,'04:55:00','05:05:00',3,3,2000),
 ('RW1024','NDLS',10,'08:40:00',NULL,3,3,2195),
 ('RW1025','NDLS',1,NULL,'10:00:00',1,1,0),
 ('RW1025','AGC',2,'13:35:00','13:45:00',1,1,195),
 ('RW1025','VGLJ',3,'17:40:00','17:50:00',1,1,410),
 ('RW1025','BPL',4,'23:10:00','23:20:00',1,1,701),
 ('RW1025','ET',5,'01:05:00','01:15:00',2,2,793),
 ('RW1025','NGP',6,'06:45:00','06:55:00',2,2,1091),
 ('RW1025','KZJ',7,'15:15:00','15:25:00',2,2,1545),
 ('RW1025','SC',8,'17:50:00','18:00:00',2,2,1677),
 ('RW1025','RC',9,'23:10:00','23:20:00',2,2,1957),
 ('RW1025','GTL',10,'01:35:00','01:45:00',3,3,2078),
 ('RW1025','DMM',11,'03:40:00','03:50:00',3,3,2180),
 ('RW1025','SBC',12,'07:15:00',NULL,3,3,2364),
 ('RW1026','SBC',1,NULL,'10:00:00',1,1,0),
 ('RW1026','DMM',2,'13:25:00','13:35:00',1,1,184),
 ('RW1026','GTL',3,'15:30:00','15:40:00',1,1,286),
 ('RW1026','RC',4,'17:55:00','18:05:00',1,1,407),
 ('RW1026','SC',5,'23:15:00','23:25:00',1,1,687),
 ('RW1026','KZJ',6,'01:50:00','02:00:00',2,2,819),
 ('RW1026','NGP',7,'10:20:00','10:30:00',2,2,1273),
 ('RW1026','ET',8,'16:00:00','16:10:00',2,2,1571),
 ('RW1026','BPL',9,'17:55:00','18:05:00',2,2,1663),
 ('RW1026','VGLJ',10,'23:25:00','23:35:00',2,2,1954),
 ('RW1026','AGC',11,'03:30:00','03:40:00',3,3,2169),
 ('RW1026','NDLS',12,'07:15:00',NULL,3,3,2364),
 ('RW1027','NDLS',1,NULL,'19:00:00',1,1,0),
 ('RW1027','CNB',2,'03:05:00','03:15:00',2,2,441),
 ('RW1027','PRYJ',3,'06:50:00','07:00:00',2,2,636),
 ('RW1027','DDU',4,'09:50:00','10:00:00',2,2,788),
 ('RW1027','PNBE',5,'13:55:00',NULL,2,2,1000),
 ('RW1028','PNBE',1,NULL,'19:00:00',1,1,0),
 ('RW1028','DDU',2,'22:55:00','23:05:00',1,1,212),
 ('RW1028','PRYJ',3,'01:55:00','02:05:00',2,2,364),
 ('RW1028','CNB',4,'05:40:00','05:50:00',2,2,559),
 ('RW1028','NDLS',5,'13:55:00',NULL,2,2,1000),
 ('RW1029','NDLS',1,NULL,'06:00:00',1,1,0),
 ('RW1029','GZB',2,'06:30:00','06:40:00',1,1,26),
 ('RW1029','ALJN',3,'08:40:00','08:50:00',1,1,132),
 ('RW1029','CNB',4,'14:30:00','14:40:00',1,1,441),
 ('RW1029','LJN',5,'16:05:00',NULL,1,1,515),
 ('RW1030','LJN',1,NULL,'17:00:00',1,1,0),
 ('RW1030','CNB',2,'18:25:00','18:35:00',1,1,74),
 ('RW1030','ALJN',3,'00:15:00','00:25:00',2,2,383),
 ('RW1030','GZB',4,'02:25:00','02:35:00',2,2,489),
 ('RW1030','NDLS',5,'03:05:00',NULL,2,2,515),
 ('RW1031','LJN',1,NULL,'18:00:00',1,1,0),
 ('RW1031','PRYJ',2,'21:40:00','21:50:00',1,1,200),
 ('RW1031','BSB',3,'00:20:00',NULL,2,2,335),
 ('RW1032','BSB',1,NULL,'06:00:00',1,1,0),
 ('RW1032','PRYJ',2,'08:30:00','08:40:00',1,1,135),
 ('RW1032','LJN',3,'12:20:00',NULL,1,1,335),
 ('RW1033','NDLS',1,NULL,'07:00:00',1,1,0),
 ('RW1033','PNP',2,'08:40:00','08:50:00',1,1,90),
 ('RW1033','KKDE',3,'10:05:00','10:15:00',1,1,158),
 ('RW1033','UMB',4,'11:05:00','11:15:00',1,1,200),
 ('RW1033','CDG',5,'12:05:00',NULL,1,1,245),
 ('RW1034','CDG',1,NULL,'16:00:00',1,1,0),
 ('RW1034','UMB',2,'16:50:00','17:00:00',1,1,45),
 ('RW1034','KKDE',3,'17:50:00','18:00:00',1,1,87),
 ('RW1034','PNP',4,'19:15:00','19:25:00',1,1,155),
 ('RW1034','NDLS',5,'21:05:00',NULL,1,1,245),
 ('RW1035','NDLS',1,NULL,'06:00:00',1,1,0),
 ('RW1035','UMB',2,'09:40:00','09:50:00',1,1,200),
 ('RW1035','LDH',3,'11:55:00','12:05:00',1,1,312),
 ('RW1035','JUC',4,'13:10:00','13:20:00',1,1,369),
 ('RW1035','BEAS',5,'14:00:00','14:10:00',1,1,405),
 ('RW1035','ASR',6,'15:00:00',NULL,1,1,447),
 ('RW1036','ASR',1,NULL,'15:00:00',1,1,0),
 ('RW1036','BEAS',2,'15:50:00','16:00:00',1,1,42),
 ('RW1036','JUC',3,'16:40:00','16:50:00',1,1,78),
 ('RW1036','LDH',4,'17:55:00','18:05:00',1,1,135),
 ('RW1036','UMB',5,'20:10:00','20:20:00',1,1,247),
 ('RW1036','NDLS',6,'00:00:00',NULL,2,2,447),
 ('RW1037','NDLS',1,NULL,'18:00:00',1,1,0),
 ('RW1037','UMB',2,'21:40:00','21:50:00',1,1,200),
 ('RW1037','LDH',3,'23:55:00','00:05:00',1,2,312),
 ('RW1037','PTKC',4,'03:10:00','03:20:00',2,2,478),
 ('RW1037','KTHU',5,'03:50:00','04:00:00',2,2,501),
 ('RW1037','JAT',6,'05:25:00',NULL,2,2,577),
 ('RW1038','JAT',1,NULL,'18:00:00',1,1,0),
 ('RW1038','KTHU',2,'19:25:00','19:35:00',1,1,76),
 ('RW1038','PTKC',3,'20:05:00','20:15:00',1,1,99),
 ('RW1038','LDH',4,'23:20:00','23:30:00',1,1,265),
 ('RW1038','UMB',5,'01:35:00','01:45:00',2,2,377),
 ('RW1038','NDLS',6,'05:25:00',NULL,2,2,577),
 ('RW1039','ADI',1,NULL,'07:00:00',1,1,0),
 ('RW1039','AII',2,'16:00:00','16:10:00',1,1,492),
 ('RW1039','JP',3,'18:40:00',NULL,1,1,627),
 ('RW1040','JP',1,NULL,'07:00:00',1,1,0),
 ('RW1040','AII',2,'09:30:00','09:40:00',1,1,135),
 ('RW1040','ADI',3,'18:40:00',NULL,1,1,627),
 ('RW1041','JP',1,NULL,'07:00:00',1,1,0),
 ('RW1041','AII',2,'09:30:00','09:40:00',1,1,135),
 ('RW1041','JU',3,'12:55:00',NULL,1,1,310),
 ('RW1042','JU',1,NULL,'15:00:00',1,1,0),
 ('RW1042','AII',2,'18:15:00','18:25:00',1,1,175),
 ('RW1042','JP',3,'20:55:00',NULL,1,1,310),
 ('RW1043','MMCT',1,NULL,'19:00:00',1,1,0),
 ('RW1043','ST',2,'23:50:00','00:00:00',1,2,263),
 ('RW1043','BRC',3,'02:25:00','02:35:00',2,2,392),
 ('RW1043','RTM',4,'07:20:00','07:30:00',2,2,650),
 ('RW1043','UJN',5,'09:15:00','09:25:00',2,2,745),
 ('RW1043','INDB',6,'10:55:00',NULL,2,2,824),
 ('RW1044','INDB',1,NULL,'19:00:00',1,1,0),
 ('RW1044','UJN',2,'20:30:00','20:40:00',1,1,79),
 ('RW1044','RTM',3,'22:25:00','22:35:00',1,1,174),
 ('RW1044','BRC',4,'03:20:00','03:30:00',2,2,432),
 ('RW1044','ST',5,'05:55:00','06:05:00',2,2,561),
 ('RW1044','MMCT',6,'10:55:00',NULL,2,2,824),
 ('RW1045','SBC',1,NULL,'08:00:00',1,1,0),
 ('RW1045','KJM',2,'08:20:00','08:30:00',1,1,14),
 ('RW1045','JTJ',3,'10:55:00','11:05:00',1,1,145),
 ('RW1045','KPD',4,'12:40:00','12:50:00',1,1,229),
 ('RW1045','AJJ',5,'14:00:00','14:10:00',1,1,290),
 ('RW1045','MAS',6,'15:30:00',NULL,1,1,359),
 ('RW1046','MAS',1,NULL,'16:00:00',1,1,0),
 ('RW1046','AJJ',2,'17:20:00','17:30:00',1,1,69),
 ('RW1046','KPD',3,'18:40:00','18:50:00',1,1,130),
 ('RW1046','JTJ',4,'20:25:00','20:35:00',1,1,214),
 ('RW1046','KJM',5,'23:00:00','23:10:00',1,1,345),
 ('RW1046','SBC',6,'23:30:00',NULL,1,1,359),
 ('RW1047','SC',1,NULL,'17:00:00',1,1,0),
 ('RW1047','KZJ',2,'19:25:00','19:35:00',1,1,132),
 ('RW1047','BZA',3,'23:35:00','23:45:00',1,1,351),
 ('RW1047','OGL',4,'02:20:00','02:30:00',2,2,489),
 ('RW1047','NLR',5,'04:40:00','04:50:00',2,2,606),
 ('RW1047','MAS',6,'08:05:00',NULL,2,2,781),
 ('RW1048','MAS',1,NULL,'17:00:00',1,1,0),
 ('RW1048','NLR',2,'20:15:00','20:25:00',1,1,175),
 ('RW1048','OGL',3,'22:35:00','22:45:00',1,1,292),
 ('RW1048','BZA',4,'01:20:00','01:30:00',2,2,430),
 ('RW1048','KZJ',5,'05:30:00','05:40:00',2,2,649),
 ('RW1048','SC',6,'08:05:00',NULL,2,2,781),
 ('RW1049','SBC',1,NULL,'18:00:00',1,1,0),
 ('RW1049','KJM',2,'18:20:00','18:30:00',1,1,14),
 ('RW1049','JTJ',3,'20:55:00','21:05:00',1,1,145),
 ('RW1049','SA',4,'23:25:00','23:35:00',1,1,270),
 ('RW1049','ED',5,'00:45:00','00:55:00',2,2,330),
 ('RW1049','CBE',6,'02:50:00','03:00:00',2,2,431),
 ('RW1049','PGT',7,'04:00:00','04:10:00',2,2,486),
 ('RW1049','SRR',8,'05:00:00','05:10:00',2,2,530),
 ('RW1049','TCR',9,'05:50:00','06:00:00',2,2,563),
 ('RW1049','ERS',10,'07:25:00',NULL,2,2,637),
 ('RW1050','ERS',1,NULL,'18:00:00',1,1,0),
 ('RW1050','TCR',2,'19:25:00','19:35:00',1,1,74),
 ('RW1050','SRR',3,'20:15:00','20:25:00',1,1,107),
 ('RW1050','PGT',4,'21:15:00','21:25:00',1,1,151),
 ('RW1050','CBE',5,'22:25:00','22:35:00',1,1,206),
 ('RW1050','ED',6,'00:30:00','00:40:00',2,2,307),
 ('RW1050','SA',7,'01:50:00','02:00:00',2,2,367),
 ('RW1050','JTJ',8,'04:20:00','04:30:00',2,2,492),
 ('RW1050','KJM',9,'06:55:00','07:05:00',2,2,623),
 ('RW1050','SBC',10,'07:25:00',NULL,2,2,637),
 ('RW1051','ERS',1,NULL,'07:00:00',1,1,0),
 ('RW1051','ALLP',2,'08:05:00','08:15:00',1,1,57),
 ('RW1051','QLN',3,'09:50:00','10:00:00',1,1,141),
 ('RW1051','TVC',4,'11:15:00',NULL,1,1,206),
 ('RW1052','TVC',1,NULL,'15:00:00',1,1,0),
 ('RW1052','QLN',2,'16:15:00','16:25:00',1,1,65),
 ('RW1052','ALLP',3,'18:00:00','18:10:00',1,1,149),
 ('RW1052','ERS',4,'19:15:00',NULL,1,1,206),
 ('RW1053','MAO',1,NULL,'08:00:00',1,1,0),
 ('RW1053','MAJN',2,'13:50:00','14:00:00',1,1,320),
 ('RW1053','CAN',3,'16:30:00','16:40:00',1,1,457),
 ('RW1053','CLT',4,'18:20:00','18:30:00',1,1,546),
 ('RW1053','SRR',5,'20:05:00','20:15:00',1,1,632),
 ('RW1053','TCR',6,'20:55:00','21:05:00',1,1,665),
 ('RW1053','ERS',7,'22:30:00',NULL,1,1,739),
 ('RW1054','ERS',1,NULL,'08:00:00',1,1,0),
 ('RW1054','TCR',2,'09:25:00','09:35:00',1,1,74),
 ('RW1054','SRR',3,'10:15:00','10:25:00',1,1,107),
 ('RW1054','CLT',4,'12:00:00','12:10:00',1,1,193),
 ('RW1054','CAN',5,'13:50:00','14:00:00',1,1,282),
 ('RW1054','MAJN',6,'16:30:00','16:40:00',1,1,419),
 ('RW1054','MAO',7,'22:30:00',NULL,1,1,739),
 ('RW1055','MAS',1,NULL,'08:00:00',1,1,0),
 ('RW1055','NLR',2,'11:15:00','11:25:00',1,1,175),
 ('RW1055','BZA',3,'16:05:00','16:15:00',1,1,431),
 ('RW1055','RJY',4,'19:00:00','19:10:00',1,1,581),
 ('RW1055','VSKP',5,'22:50:00','23:00:00',1,1,781),
 ('RW1055','BAM',6,'04:05:00','04:15:00',2,2,1058),
 ('RW1055','BBS',7,'07:20:00',NULL,2,2,1224),
 ('RW1056','BBS',1,NULL,'08:00:00',1,1,0),
 ('RW1056','BAM',2,'11:05:00','11:15:00',1,1,166),
 ('RW1056','VSKP',3,'16:20:00','16:30:00',1,1,443),
 ('RW1056','RJY',4,'20:10:00','20:20:00',1,1,643),
 ('RW1056','BZA',5,'23:05:00','23:15:00',1,1,793),
 ('RW1056','NLR',6,'03:55:00','04:05:00',2,2,1049),
 ('RW1056','MAS',7,'07:20:00',NULL,2,2,1224),
 ('RW1057','BBS',1,NULL,'08:00:00',1,1,0),
 ('RW1057','CTC',2,'08:35:00','08:45:00',1,1,28),
 ('RW1057','BHC',3,'10:55:00','11:05:00',1,1,144),
 ('RW1057','KGP',4,'14:25:00','14:35:00',1,1,324),
 ('RW1057','HWH',5,'16:45:00',NULL,1,1,439),
 ('RW1058','HWH',1,NULL,'08:00:00',1,1,0),
 ('RW1058','KGP',2,'10:10:00','10:20:00',1,1,115),
 ('RW1058','BHC',3,'13:40:00','13:50:00',1,1,295),
 ('RW1058','CTC',4,'16:00:00','16:10:00',1,1,411),
 ('RW1058','BBS',5,'16:45:00',NULL,1,1,439),
 ('RW1059','HWH',1,NULL,'15:00:00',1,1,0),
 ('RW1059','MLDT',2,'21:05:00','21:15:00',1,1,332),
 ('RW1059','NJP',3,'01:30:00','01:40:00',2,2,565),
 ('RW1059','NBQ',4,'06:10:00','06:20:00',2,2,810),
 ('RW1059','GHY',5,'09:15:00',NULL,2,2,967),
 ('RW1060','GHY',1,NULL,'15:00:00',1,1,0),
 ('RW1060','NBQ',2,'17:55:00','18:05:00',1,1,157),
 ('RW1060','NJP',3,'22:35:00','22:45:00',1,1,402),
 ('RW1060','MLDT',4,'03:00:00','03:10:00',2,2,635),
 ('RW1060','HWH',5,'09:15:00',NULL,2,2,967),
 ('RW1061','HWH',1,NULL,'07:00:00',1,1,0),
 ('RW1061','KGP',2,'09:10:00','09:20:00',1,1,115),
 ('RW1061','TATA',3,'11:50:00','12:00:00',1,1,249),
 ('RW1061','RNC',4,'15:10:00',NULL,1,1,419),
 ('RW1062','RNC',1,NULL,'07:00:00',1,1,0),
 ('RW1062','TATA',2,'10:10:00','10:20:00',1,1,170),
 ('RW1062','KGP',3,'12:50:00','13:00:00',1,1,304),
 ('RW1062','HWH',4,'15:10:00',NULL,1,1,419),
 ('RW1063','PNBE',1,NULL,'07:00:00',1,1,0),
 ('RW1063','GAYA',2,'08:45:00','08:55:00',1,1,92),
 ('RW1063','BKSC',3,'11:45:00','11:55:00',1,1,245),
 ('RW1063','RNC',4,'13:55:00',NULL,1,1,355),
 ('RW1064','RNC',1,NULL,'07:00:00',1,1,0),
 ('RW1064','BKSC',2,'09:00:00','09:10:00',1,1,110),
 ('RW1064','GAYA',3,'12:00:00','12:10:00',1,1,263),
 ('RW1064','PNBE',4,'13:55:00',NULL,1,1,355),
 ('RW1065','BSB',1,NULL,'08:00:00',1,1,0),
 ('RW1065','DDU',2,'08:20:00','08:30:00',1,1,18),
 ('RW1065','PNBE',3,'12:25:00',NULL,1,1,230),
 ('RW1066','PNBE',1,NULL,'16:00:00',1,1,0),
 ('RW1066','DDU',2,'19:55:00','20:05:00',1,1,212),
 ('RW1066','BSB',3,'20:25:00',NULL,1,1,230),
 ('RW1067','BPL',1,NULL,'08:00:00',1,1,0),
 ('RW1067','UJN',2,'11:20:00','11:30:00',1,1,183),
 ('RW1067','INDB',3,'13:00:00',NULL,1,1,262),
 ('RW1068','INDB',1,NULL,'16:00:00',1,1,0),
 ('RW1068','UJN',2,'17:30:00','17:40:00',1,1,79),
 ('RW1068','BPL',3,'21:00:00',NULL,1,1,262);


-- Combine the source datasets into one validated import. Reuse shared station codes.
-- IGNORE is limited to duplicate station keys in private staging, never business data.
INSERT IGNORE INTO rw_final_stations SELECT * FROM rw_major_stations;
INSERT INTO rw_final_trains SELECT * FROM rw_major_trains;
INSERT INTO rw_final_stops SELECT * FROM rw_major_stops;
INSERT INTO rw_final_days
SELECT t.number,d.day FROM rw_major_trains t CROSS JOIN
 (SELECT 'MONDAY' AS day UNION ALL SELECT 'TUESDAY' UNION ALL SELECT 'WEDNESDAY'
 UNION ALL SELECT 'THURSDAY' UNION ALL SELECT 'FRIDAY' UNION ALL SELECT 'SATURDAY' UNION ALL SELECT 'SUNDAY') d;
INSERT INTO rw_final_classes
SELECT t.number,c.class,c.coaches,c.seats,c.base,c.rate,c.minimum FROM rw_major_trains t CROSS JOIN
 (SELECT 'SL' AS class,2 AS coaches,72 AS seats,40 AS base,0.60 AS rate,120 AS minimum
 UNION ALL SELECT 'CLASS_3A',2,72,100,1.50,300 UNION ALL SELECT 'CLASS_2A',1,48,150,2.20,450) c;
DROP TEMPORARY TABLE rw_major_new;
DROP TEMPORARY TABLE rw_major_stations;
DROP TEMPORARY TABLE rw_major_trains;
DROP TEMPORARY TABLE rw_major_stops;
DROP TEMPORARY TABLE IF EXISTS rw_final_new;
CREATE TEMPORARY TABLE rw_final_new(number VARCHAR(10) PRIMARY KEY);

-- Replace only this loader's own routine, never an application table.
DROP PROCEDURE IF EXISTS railwize_import_final_catalogue_v1;
DELIMITER $$
CREATE PROCEDURE railwize_import_final_catalogue_v1()
BEGIN
 DECLARE n_stations INT DEFAULT 0;
 DECLARE n_trains INT DEFAULT 0;
 DECLARE n_stops INT DEFAULT 0;
 DECLARE n_coordinates INT DEFAULT 0;
 DECLARE n_names INT DEFAULT 0;
 DECLARE lock_acquired INT DEFAULT 0;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION
 BEGIN
  ROLLBACK;
  IF lock_acquired=1 THEN DO RELEASE_LOCK('railwize_final_catalogue_v1'); END IF;
  RESIGNAL;
 END;
 SELECT GET_LOCK('railwize_final_catalogue_v1',10) INTO lock_acquired;
 IF NOT(lock_acquired <=> 1) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: another final catalogue import is running.';
 END IF;
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE engine='InnoDB' AND
   ((table_schema='railway_train_db' AND table_name IN
    ('stations','trains','train_seat_classes','train_operating_days','train_class_configurations'))
    OR (table_schema='railway_schedule_db' AND table_name='schedules')))<>6 THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: start current train/schedule services once; six InnoDB catalogue tables are required.';
 END IF;
 START TRANSACTION;
 -- Names are display metadata; preserve custom names. Operational metadata must match.
 IF EXISTS(SELECT 1 FROM trains t JOIN rw_final_trains x ON x.number=t.train_number
  LEFT JOIN stations a ON a.station_id=t.source_station_id
  LEFT JOIN stations b ON b.station_id=t.destination_station_id
  WHERE NOT(t.category<=>x.category) OR NOT(a.station_code<=>x.source_code)
   OR NOT(b.station_code<=>x.destination_code)) THEN
  SELECT t.train_number AS conflicting_train,t.category,a.station_code AS source_code,b.station_code AS destination_code
  FROM trains t JOIN rw_final_trains x ON x.number=t.train_number
  LEFT JOIN stations a ON a.station_id=t.source_station_id LEFT JOIN stations b ON b.station_id=t.destination_station_id
  WHERE NOT(t.category<=>x.category) OR NOT(a.station_code<=>x.source_code) OR NOT(b.station_code<=>x.destination_code);
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: existing train endpoints/category conflict. Nothing changed; inspect conflict rows.';
 END IF;
 IF EXISTS(SELECT 1 FROM railway_schedule_db.schedules s JOIN rw_final_trains x ON x.number=s.train_number
  LEFT JOIN trains t ON t.train_number=x.number WHERE t.train_id IS NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: an orphan timetable exists for a dataset train. Review it without deleting booked data.';
 END IF;
 IF EXISTS(SELECT 1 FROM rw_final_stops x JOIN trains t ON t.train_number=x.number
  LEFT JOIN railway_schedule_db.schedules s ON s.train_number=x.number AND s.stop_sequence=x.sequence
  WHERE s.id IS NULL OR NOT(s.station_code<=>x.code) OR NOT(s.arrival_time<=>x.arrival)
   OR NOT(s.departure_time<=>x.departure) OR NOT(s.day_number<=>x.arrival_day)
   OR NOT(COALESCE(s.departure_day_number,s.day_number)<=>x.departure_day) OR NOT(s.distance_km<=>x.km)) THEN
  SELECT DISTINCT x.number AS conflicting_timetable FROM rw_final_stops x JOIN trains t ON t.train_number=x.number
  LEFT JOIN railway_schedule_db.schedules s ON s.train_number=x.number AND s.stop_sequence=x.sequence
  WHERE s.id IS NULL OR NOT(s.station_code<=>x.code) OR NOT(s.arrival_time<=>x.arrival)
   OR NOT(s.departure_time<=>x.departure) OR NOT(s.day_number<=>x.arrival_day)
   OR NOT(COALESCE(s.departure_day_number,s.day_number)<=>x.departure_day) OR NOT(s.distance_km<=>x.km);
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: an existing timetable is incomplete or different. Nothing overwritten.';
 END IF;
 IF EXISTS(SELECT 1 FROM railway_schedule_db.schedules s JOIN rw_final_trains t ON t.number=s.train_number
  LEFT JOIN rw_final_stops x ON x.number=s.train_number AND x.sequence=s.stop_sequence WHERE x.number IS NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: a dataset timetable contains extra stops. Nothing overwritten.';
 END IF;
 -- Compare both membership and counts: a count alone would miss substituted weekdays/classes.
 IF EXISTS(SELECT 1 FROM trains t JOIN rw_final_trains x ON x.number=t.train_number WHERE
  (SELECT COUNT(*) FROM train_operating_days d WHERE d.train_id=t.train_id)<>
  (SELECT COUNT(*) FROM rw_final_days d WHERE d.number=t.train_number)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: operating-day count differs. Nothing overwritten.';
 END IF;
 IF EXISTS(SELECT 1 FROM rw_final_days d JOIN trains t ON t.train_number=d.number
  LEFT JOIN train_operating_days a ON a.train_id=t.train_id AND a.operating_day=d.day WHERE a.train_id IS NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: existing operating days differ or are incomplete. Published days were not changed.';
 END IF;
 IF EXISTS(SELECT 1 FROM trains t JOIN rw_final_trains x ON x.number=t.train_number WHERE
  (SELECT COUNT(*) FROM train_seat_classes c WHERE c.train_id=t.train_id)<>
  (SELECT COUNT(*) FROM rw_final_classes c WHERE c.number=t.train_number)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: class/configuration count differs. Nothing overwritten.';
 END IF;
 IF EXISTS(SELECT 1 FROM rw_final_classes c JOIN trains t ON t.train_number=c.number
  LEFT JOIN train_seat_classes a ON a.train_id=t.train_id AND a.seat_class=c.class WHERE a.train_id IS NULL) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: existing seat classes differ or are incomplete. Nothing overwritten.';
 END IF;
 IF EXISTS(SELECT 1 FROM trains t JOIN rw_final_trains x ON x.number=t.train_number WHERE
  (SELECT COUNT(*) FROM train_class_configurations c WHERE c.train_id=t.train_id)<>
  (SELECT COUNT(*) FROM rw_final_classes c WHERE c.number=t.train_number)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: class/configuration count differs. Nothing overwritten.';
 END IF;
 IF EXISTS(SELECT 1 FROM rw_final_classes c JOIN trains t ON t.train_number=c.number
  LEFT JOIN train_class_configurations a ON a.train_id=t.train_id AND a.seat_class=c.class
  WHERE a.train_id IS NULL OR NOT(a.coach_count<=>c.coaches) OR NOT(a.seats_per_coach<=>c.seats)
   OR NOT(a.base_fare<=>c.base) OR NOT(a.rate_per_km<=>c.rate) OR NOT(a.minimum_fare<=>c.minimum)) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: existing capacity/fare configuration differs or is incomplete. Nothing overwritten.';
 END IF;
 INSERT INTO rw_final_new
 SELECT x.number FROM rw_final_trains x LEFT JOIN trains t ON t.train_number=x.number WHERE t.train_id IS NULL;
 INSERT INTO stations(station_code,station_name,city,state,zone,latitude,longitude)
 SELECT x.code,x.name,x.city,x.state,x.zone,x.latitude,x.longitude FROM rw_final_stations x
 LEFT JOIN stations s ON s.station_code=x.code WHERE s.station_id IS NULL;
 SET n_stations=ROW_COUNT();
 -- Repair only known missing/invalid coordinates; preserve every valid existing pair.
 UPDATE stations s JOIN rw_final_stations x ON x.code=s.station_code
 SET s.latitude=x.latitude,s.longitude=x.longitude
 WHERE s.station_id>0 AND (s.latitude IS NULL OR s.longitude IS NULL OR (s.latitude=0 AND s.longitude=0)
  OR s.latitude NOT BETWEEN -90 AND 90 OR s.longitude NOT BETWEEN -180 AND 180);
 SET n_coordinates=ROW_COUNT();
 INSERT INTO trains(train_number,train_name,category,source_station_id,destination_station_id)
 SELECT x.number,x.name,x.category,a.station_id,b.station_id FROM rw_final_trains x JOIN rw_final_new n ON n.number=x.number
 JOIN stations a ON a.station_code=x.source_code JOIN stations b ON b.station_code=x.destination_code;
 SET n_trains=ROW_COUNT();
 IF n_trains<>(SELECT COUNT(*) FROM rw_final_new) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: train endpoints could not be resolved.';
 END IF;
 INSERT INTO train_operating_days(train_id,operating_day)
 SELECT t.train_id,d.day FROM rw_final_days d JOIN rw_final_new n ON n.number=d.number JOIN trains t ON t.train_number=d.number;
 INSERT INTO train_seat_classes(train_id,seat_class)
 SELECT t.train_id,c.class FROM rw_final_classes c JOIN rw_final_new n ON n.number=c.number JOIN trains t ON t.train_number=c.number;
 INSERT INTO train_class_configurations(train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare)
 SELECT t.train_id,c.class,c.coaches,c.seats,c.base,c.rate,c.minimum FROM rw_final_classes c
 JOIN rw_final_new n ON n.number=c.number JOIN trains t ON t.train_number=c.number;
 INSERT INTO railway_schedule_db.schedules(train_number,station_code,stop_sequence,arrival_time,departure_time,
  day_number,departure_day_number,distance_km)
 SELECT x.number,x.code,x.sequence,x.arrival,x.departure,x.arrival_day,x.departure_day,x.km
 FROM rw_final_stops x JOIN rw_final_new n ON n.number=x.number ORDER BY x.number,x.sequence;
 SET n_stops=ROW_COUNT();
 UPDATE trains SET train_name = 'Nizamuddin Lucknow Express' WHERE train_number = 'RW901' AND train_name = 'Railwize Demo - Delhi Agra Lucknow Layout Explorer';
 SET n_names=n_names+ROW_COUNT();
 UPDATE trains SET train_name = 'Lucknow Varanasi Express' WHERE train_number = 'RW902' AND train_name = 'Railwize Demo - Lucknow Varanasi Connection';
 SET n_names=n_names+ROW_COUNT();
 UPDATE trains SET train_name = 'Lucknow Varanasi Intercity' WHERE train_number = 'RW903' AND train_name = 'Railwize Demo - Connection Too Short';
 SET n_names=n_names+ROW_COUNT();
 UPDATE trains SET train_name = 'Lucknow Varanasi Night Express' WHERE train_number = 'RW904' AND train_name = 'Railwize Demo - Connection Too Long';
 SET n_names=n_names+ROW_COUNT();
 UPDATE trains SET train_name = 'Bandra Sabarmati Express' WHERE train_number = 'RW905' AND train_name = 'Railwize Demo - Bandra Sabarmati Nearby Alternative';
 SET n_names=n_names+ROW_COUNT();
 UPDATE trains SET train_name = 'Nizamuddin Lucknow Night Express' WHERE train_number = 'RW906' AND train_name = 'Railwize Demo - Monday Midnight Crossing';
 SET n_names=n_names+ROW_COUNT();
 UPDATE trains SET train_name = 'Jammu Katra Express' WHERE train_number = 'RW907' AND train_name = 'Railwize Demo - Jammu Katra Waitlist Lab';
 SET n_names=n_names+ROW_COUNT();
 COMMIT;
 DO RELEASE_LOCK('railwize_final_catalogue_v1');
 SET lock_acquired=0;
 SELECT 'FINAL CATALOGUE IMPORT SUCCEEDED' AS result,n_stations AS new_stations,n_trains AS new_trains,
  n_stops AS new_timetable_stops,(SELECT COUNT(*) FROM rw_final_trains)-n_trains AS matching_existing_trains,
  n_coordinates AS repaired_coordinates,n_names AS updated_legacy_names;
END$$
DELIMITER ;
CALL railwize_import_final_catalogue_v1();
DROP PROCEDURE railwize_import_final_catalogue_v1;

-- Reports below are diagnostics, NOT a success signal after any failed CALL above.
SELECT 'EXPECTED DATASET' AS scope,(SELECT COUNT(*) FROM rw_final_stations) AS stations,
 (SELECT COUNT(*) FROM rw_final_trains) AS trains,(SELECT COUNT(*) FROM rw_final_stops) AS timetable_stops,
 (SELECT COUNT(*) FROM rw_final_classes) AS class_configurations;
SELECT COUNT(*) AS present_dataset_stations FROM stations s JOIN rw_final_stations x ON x.code=s.station_code;
SELECT COUNT(*) AS present_dataset_trains FROM trains t JOIN rw_final_trains x ON x.number=t.train_number;
SELECT COUNT(*) AS present_dataset_stops FROM railway_schedule_db.schedules s JOIN rw_final_trains x ON x.number=s.train_number;

-- Mumbai -> Delhi: direct service and fares for every available class.
SELECT t.train_number,t.train_name,a.station_code AS boarding,b.station_code AS destination,
 a.departure_time,a.departure_day_number,b.arrival_time,b.day_number AS arrival_day,
 b.distance_km-a.distance_km AS journey_km,c.seat_class,c.coach_count*c.seats_per_coach AS configured_capacity,
 ROUND(GREATEST(c.minimum_fare,c.base_fare+c.rate_per_km*(b.distance_km-a.distance_km)),2) AS fare_per_person
FROM trains t JOIN railway_schedule_db.schedules a ON a.train_number=t.train_number AND a.station_code='MMCT'
JOIN railway_schedule_db.schedules b ON b.train_number=t.train_number AND b.station_code='NDLS' AND b.stop_sequence>a.stop_sequence
JOIN train_class_configurations c ON c.train_id=t.train_id ORDER BY t.train_number,c.seat_class;
-- Configured capacity is not remaining availability: use the app for date/segment/cancellation-aware availability.
SELECT DATE(UTC_TIMESTAMP()+INTERVAL 330 MINUTE)+INTERVAL 7 DAY AS suggested_boarding_date,
 DATE_ADD(DATE(UTC_TIMESTAMP()+INTERVAL 330 MINUTE),
 INTERVAL (7-WEEKDAY(DATE(UTC_TIMESTAMP()+INTERVAL 330 MINUTE))) DAY) AS next_monday_source_date;
SELECT station_code,station_name,latitude,longitude FROM stations
WHERE latitude IS NULL OR longitude IS NULL OR (latitude=0 AND longitude=0)
 OR latitude NOT BETWEEN -90 AND 90 OR longitude NOT BETWEEN -180 AND 180;
-- Any rows in the last result need manual location review (e.g. custom station codes).
DROP TEMPORARY TABLE rw_final_new;
DROP TEMPORARY TABLE rw_final_stops;
DROP TEMPORARY TABLE rw_final_classes;
DROP TEMPORARY TABLE rw_final_days;
DROP TEMPORARY TABLE rw_final_trains;
DROP TEMPORARY TABLE rw_final_stations;
