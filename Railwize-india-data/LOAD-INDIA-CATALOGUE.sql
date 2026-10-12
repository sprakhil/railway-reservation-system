-- Railwize India showcase catalogue, version 2026-10-11. MySQL 8.0.
-- Read INDIA-DATA-GUIDE.txt first. Stop application services during import.
-- This is a researched timetable snapshot with selected stops; not a live railway feed.
-- RW901-RW907 are deliberately fictional test services. All fares/capacities are sample values.
-- No application rows are deleted or updated. Existing stations are reused by station_code.
-- ANY matching train number or timetable causes the entire import to abort before insertion.
-- Run the WHOLE file in one MySQL Workbench connection (not just the current statement).
-- Requires SELECT, INSERT, CREATE TEMPORARY TABLES, CREATE ROUTINE, ALTER ROUTINE, EXECUTE.
-- Start current train/schedule services once with DDL_AUTO=update to create the schema, then stop.
-- Both schemas must be on the SAME MySQL server and all six target tables must be InnoDB.
-- For custom schema names replace railway_train_db and railway_schedule_db throughout this file.
SET NAMES utf8mb4;
USE railway_train_db;
-- These are session-private staging tables, not application tables.
DROP TEMPORARY TABLE IF EXISTS rw_india_stations;
DROP TEMPORARY TABLE IF EXISTS rw_india_trains;
DROP TEMPORARY TABLE IF EXISTS rw_india_days;
DROP TEMPORARY TABLE IF EXISTS rw_india_classes;
DROP TEMPORARY TABLE IF EXISTS rw_india_stops;
CREATE TEMPORARY TABLE rw_india_stations (
 code VARCHAR(10) PRIMARY KEY,name VARCHAR(255),city VARCHAR(255),state VARCHAR(255),zone VARCHAR(255),latitude DOUBLE,longitude DOUBLE);
CREATE TEMPORARY TABLE rw_india_trains (
 number VARCHAR(10) PRIMARY KEY,name VARCHAR(255),category VARCHAR(32),source_code VARCHAR(10),destination_code VARCHAR(10));
CREATE TEMPORARY TABLE rw_india_days (number VARCHAR(10),day VARCHAR(16),PRIMARY KEY(number,day));
CREATE TEMPORARY TABLE rw_india_classes (
 number VARCHAR(10),class VARCHAR(16),coaches INT,seats INT,base DECIMAL(9,2),rate DECIMAL(9,2),minimum DECIMAL(9,2),PRIMARY KEY(number,class));
CREATE TEMPORARY TABLE rw_india_stops (
 number VARCHAR(10),code VARCHAR(10),sequence INT,arrival TIME,departure TIME,arrival_day INT,departure_day INT,km INT,
 PRIMARY KEY(number,sequence),UNIQUE(number,code));

INSERT INTO rw_india_stations (code,name,city,state,zone,latitude,longitude) VALUES
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

INSERT INTO rw_india_trains (number,name,category,source_code,destination_code) VALUES
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

INSERT INTO rw_india_days (number,day) VALUES
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

INSERT INTO rw_india_classes (number,class,coaches,seats,base,rate,minimum) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
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

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12123','CSMT',1,NULL,'17:10:00',1,1,0),
 ('12123','KJT',2,'18:33:00','18:35:00',1,1,100),
 ('12123','LNL',3,'19:18:00','19:20:00',1,1,128),
 ('12123','SVJR',4,'20:09:00','20:10:00',1,1,190),
 ('12123','PUNE',5,'20:25:00',NULL,1,1,192);


-- 12124 Pune - Mumbai CSMT Deccan Queen
-- https://erail.in/train-enquiry/12124

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12124','PUNE',1,NULL,'07:15:00',1,1,0),
 ('12124','LNL',2,'08:06:00','08:07:00',1,1,64),
 ('12124','DR',3,'10:03:00','10:05:00',1,1,183),
 ('12124','CSMT',4,'10:25:00',NULL,1,1,192);


-- 12005 New Delhi - Kalka Shatabdi
-- https://erail.in/train-enquiry/12005

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12005','NDLS',1,NULL,'17:15:00',1,1,0),
 ('12005','PNP',2,'18:18:00','18:20:00',1,1,89),
 ('12005','KKDE',3,'19:00:00','19:02:00',1,1,157),
 ('12005','UMB',4,'19:50:00','19:53:00',1,1,198),
 ('12005','CDG',5,'20:30:00','20:38:00',1,1,266),
 ('12005','KLK',6,'21:15:00',NULL,1,1,303);


-- 12006 Kalka - New Delhi Shatabdi
-- https://erail.in/train-enquiry/12006

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12006','KLK',1,NULL,'06:15:00',1,1,0),
 ('12006','CDG',2,'06:45:00','06:53:00',1,1,38),
 ('12006','UMB',3,'07:33:00','07:38:00',1,1,106),
 ('12006','KKDE',4,'08:08:00','08:10:00',1,1,147),
 ('12006','PNP',5,'08:50:00','08:52:00',1,1,214),
 ('12006','NDLS',6,'10:15:00',NULL,1,1,303);


-- 22436 New Delhi - Varanasi Vande Bharat
-- https://erail.in/train-enquiry/22436

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('22436','NDLS',1,NULL,'06:00:00',1,1,0),
 ('22436','CNB',2,'10:08:00','10:10:00',1,1,441),
 ('22436','PRYJ',3,'12:08:00','12:10:00',1,1,636),
 ('22436','BSB',4,'14:00:00',NULL,1,1,771);


-- 22435 Varanasi - New Delhi Vande Bharat
-- https://erail.in/train-enquiry/22435

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('22435','BSB',1,NULL,'15:00:00',1,1,0),
 ('22435','PRYJ',2,'16:30:00','16:32:00',1,1,135),
 ('22435','CNB',3,'18:30:00','18:32:00',1,1,330),
 ('22435','NDLS',4,'23:00:00',NULL,1,1,771);


-- 12425 New Delhi - Jammu Tawi Rajdhani
-- https://erail.in/train-enquiry/12425

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12425','NDLS',1,NULL,'20:40:00',1,1,0),
 ('12425','LDH',2,'00:28:00','00:38:00',2,2,312),
 ('12425','PTKC',3,'03:10:00','03:12:00',2,2,478),
 ('12425','KTHU',4,'03:41:00','03:43:00',2,2,501),
 ('12425','JAT',5,'05:00:00',NULL,2,2,577);


-- 12426 Jammu Tawi - New Delhi Rajdhani
-- https://erail.in/train-enquiry/12426

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('12426','JAT',1,NULL,'21:25:00',1,1,0),
 ('12426','KTHU',2,'22:31:00','22:33:00',1,1,77),
 ('12426','PTKC',3,'23:05:00','23:07:00',1,1,100),
 ('12426','LDH',4,'01:35:00','01:45:00',2,2,265),
 ('12426','NDLS',5,'05:55:00',NULL,2,2,577);


-- RW901 Nizamuddin Lucknow Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW901','NZM',1,NULL,'06:00:00',1,1,0),
 ('RW901','AGC',2,'09:00:00','09:10:00',1,1,188),
 ('RW901','CNB',3,'13:00:00','13:10:00',1,1,470),
 ('RW901','LJN',4,'14:30:00',NULL,1,1,544);


-- RW902 Lucknow Varanasi Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW902','LJN',1,NULL,'16:00:00',1,1,0),
 ('RW902','PRYJ',2,'19:00:00','19:10:00',1,1,200),
 ('RW902','BSB',3,'21:00:00',NULL,1,1,335);


-- RW903 Lucknow Varanasi Intercity
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW903','LJN',1,NULL,'14:50:00',1,1,0),
 ('RW903','BSB',2,'20:00:00',NULL,1,1,335);


-- RW904 Lucknow Varanasi Night Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW904','LJN',1,NULL,'23:45:00',1,1,0),
 ('RW904','BSB',2,'05:00:00',NULL,2,2,335);


-- RW905 Bandra Sabarmati Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW905','BDTS',1,NULL,'08:00:00',1,1,0),
 ('RW905','ST',2,'11:00:00','11:10:00',1,1,252),
 ('RW905','BRC',3,'13:00:00','13:10:00',1,1,381),
 ('RW905','SBT',4,'15:00:00',NULL,1,1,490);


-- RW906 Nizamuddin Lucknow Night Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW906','NZM',1,NULL,'21:00:00',1,1,0),
 ('RW906','AGC',2,'23:55:00','00:05:00',1,2,188),
 ('RW906','LJN',3,'06:00:00',NULL,2,2,544);


-- RW907 Jammu Katra Express
-- Fictional Railwize test service; timings and distances are illustrative.

INSERT INTO rw_india_stops (number,code,sequence,arrival,departure,arrival_day,departure_day,km) VALUES
 ('RW907','JAT',1,NULL,'10:00:00',1,1,0),
 ('RW907','SVDK',2,'12:00:00',NULL,1,1,78);


-- Read-only preflight reports. Existing stations are retained exactly as they are.
SELECT t.train_number AS conflicting_train FROM trains t JOIN rw_india_trains seed ON seed.number=t.train_number;
SELECT DISTINCT s.train_number AS conflicting_timetable FROM railway_schedule_db.schedules s JOIN rw_india_trains seed ON seed.number=s.train_number;
SELECT s.station_code AS reused_station,s.station_name,s.latitude,s.longitude,
 seed.latitude AS sample_latitude,seed.longitude AS sample_longitude
FROM stations s JOIN rw_india_stations seed ON seed.code=s.station_code;

-- Only this dataset's loader routine is replaced/removed; no business table is dropped.
DROP PROCEDURE IF EXISTS railwize_import_india_20261011;
DELIMITER $$
CREATE PROCEDURE railwize_import_india_20261011()
BEGIN
 DECLARE loaded_stations INT DEFAULT 0;
 DECLARE loaded_trains INT DEFAULT 0;
 DECLARE loaded_stops INT DEFAULT 0;
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 -- Missing/nontransactional tables must be detected before touching any application rows.
 IF (SELECT COUNT(*) FROM information_schema.tables WHERE engine='InnoDB' AND
    ((table_schema='railway_train_db' AND table_name IN ('stations','trains','train_seat_classes','train_operating_days','train_class_configurations'))
     OR (table_schema='railway_schedule_db' AND table_name='schedules'))) <> 6 THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: start current services once; all six catalogue tables must exist and use InnoDB.';
 END IF;
 START TRANSACTION;
 IF EXISTS(SELECT 1 FROM trains t JOIN rw_india_trains seed ON seed.number=t.train_number) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: a dataset train/timetable already exists. Nothing overwritten. See preflight conflicts.';
 END IF;
 -- MySQL cannot reference the same temporary table twice in a single statement.
 IF EXISTS(SELECT 1 FROM railway_schedule_db.schedules s JOIN rw_india_trains seed ON seed.number=s.train_number) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: a dataset train/timetable already exists. Nothing overwritten. See preflight conflicts.';
 END IF;
 INSERT INTO stations(station_code,station_name,city,state,zone,latitude,longitude)
 SELECT seed.code,seed.name,seed.city,seed.state,seed.zone,seed.latitude,seed.longitude
 FROM rw_india_stations seed LEFT JOIN stations s ON s.station_code=seed.code WHERE s.station_id IS NULL;
 SET loaded_stations=ROW_COUNT();
 INSERT INTO trains(train_number,train_name,category,source_station_id,destination_station_id)
 SELECT seed.number,seed.name,seed.category,origin.station_id,destination.station_id
 FROM rw_india_trains seed JOIN stations origin ON origin.station_code=seed.source_code
 JOIN stations destination ON destination.station_code=seed.destination_code;
 SET loaded_trains=ROW_COUNT();
 IF loaded_trains<>(SELECT COUNT(*) FROM rw_india_trains) THEN
   SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Import aborted: not every train had valid source and destination stations.';
 END IF;
 INSERT INTO train_seat_classes(train_id,seat_class)
 SELECT t.train_id,c.class FROM rw_india_classes c JOIN trains t ON t.train_number=c.number;
 INSERT INTO train_class_configurations(train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare)
 SELECT t.train_id,c.class,c.coaches,c.seats,c.base,c.rate,c.minimum FROM rw_india_classes c JOIN trains t ON t.train_number=c.number;
 INSERT INTO train_operating_days(train_id,operating_day)
 SELECT t.train_id,d.day FROM rw_india_days d JOIN trains t ON t.train_number=d.number;
 INSERT INTO railway_schedule_db.schedules(train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km)
 SELECT number,code,sequence,arrival,departure,arrival_day,departure_day,km FROM rw_india_stops ORDER BY number,sequence;
 SET loaded_stops=ROW_COUNT();
 COMMIT;
 SELECT 'IMPORT SUCCEEDED' AS result,loaded_stations AS new_stations,loaded_trains AS new_trains,loaded_stops AS timetable_stops;
END$$
DELIMITER ;
CALL railwize_import_india_20261011();
DROP PROCEDURE railwize_import_india_20261011;

-- Verification reports, scoped to this dataset. Counts after a rejected reimport are not a new successful import.
SELECT COUNT(*) AS dataset_stations FROM stations s JOIN rw_india_stations seed ON seed.code=s.station_code;
SELECT COUNT(*) AS dataset_trains FROM trains t JOIN rw_india_trains seed ON seed.number=t.train_number;
SELECT COUNT(*) AS dataset_stops FROM railway_schedule_db.schedules s JOIN rw_india_trains seed ON seed.number=s.train_number;
SELECT t.train_number,t.train_name,o.station_code AS origin,d.station_code AS destination
 FROM trains t JOIN rw_india_trains seed ON seed.number=t.train_number
 JOIN stations o ON o.station_id=t.source_station_id JOIN stations d ON d.station_id=t.destination_station_id ORDER BY t.train_number;
SELECT t.train_number,c.seat_class,c.coach_count,c.seats_per_coach,c.coach_count*c.seats_per_coach AS capacity,
 c.base_fare,c.rate_per_km,c.minimum_fare FROM trains t JOIN rw_india_trains seed ON seed.number=t.train_number
 JOIN train_class_configurations c ON c.train_id=t.train_id ORDER BY t.train_number,c.seat_class;
SELECT s.* FROM railway_schedule_db.schedules s JOIN rw_india_trains seed ON seed.number=s.train_number ORDER BY s.train_number,s.stop_sequence;
-- Nearby lookup needs existing stations to have sensible coordinates too. Review any rows here in Admin > Stations.
SELECT s.station_code,s.station_name,s.latitude,s.longitude FROM stations s
 JOIN rw_india_stations seed ON seed.code=s.station_code
 WHERE s.latitude IS NULL OR s.longitude IS NULL OR s.latitude NOT BETWEEN -90 AND 90 OR s.longitude NOT BETWEEN -180 AND 180;
-- Date helpers evaluated at import time in IST; source run dates and boarding dates can differ.
SELECT DATE(UTC_TIMESTAMP()+INTERVAL 330 MINUTE)+INTERVAL 7 DAY AS suggested_boarding_date,
 DATE_ADD(DATE(UTC_TIMESTAMP()+INTERVAL 330 MINUTE),INTERVAL (7-WEEKDAY(DATE(UTC_TIMESTAMP()+INTERVAL 330 MINUTE))) DAY) AS next_monday_run_date;
