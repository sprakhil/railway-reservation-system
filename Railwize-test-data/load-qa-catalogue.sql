-- Railwize synthetic QA catalogue. Start the apps once so Hibernate creates tables, then stop them before import.
-- MySQL 8; run the entire file in MySQL Workbench as a user with INSERT and CREATE ROUTINE privileges.
-- Inserts only catalogue/timetable records. Does not delete or update existing application data.
-- Existing matching QA codes cause rollback; do not rerun against a previously loaded dataset.
-- All affected tables must use InnoDB for the cross-schema transaction.
USE railway_train_db;
DELIMITER $$
CREATE PROCEDURE railwize_load_qa_20261004()
BEGIN
 DECLARE EXIT HANDLER FOR SQLEXCEPTION BEGIN ROLLBACK; RESIGNAL; END;
 START TRANSACTION;
 IF EXISTS (SELECT 1 FROM stations WHERE station_code IN ('QAMUM','QADAD','QASRT','QABRC','QAAHM','QAMAN','QAPUN','QAZERO','QAWLS','QAWLD'))
 OR EXISTS (SELECT 1 FROM trains WHERE train_number IN ('QA101','QA102','QA103','QA104','QA105','QA106','QA107','QA108','QA109','QA110','QA111','QA112','QA113','QA114','QA115'))
 OR EXISTS (SELECT 1 FROM railway_schedule_db.schedules WHERE train_number IN ('QA101','QA102','QA103','QA104','QA105','QA106','QA107','QA108','QA109','QA110','QA111','QA112','QA113','QA114','QA115')) THEN
 SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='QA codes already exist. No catalogue data changed. Use the existing dataset or a fresh test database.';
 END IF;

INSERT INTO stations (station_code,station_name,city,state,zone,latitude,longitude) VALUES
 ('QAMUM','QA Mumbai Central','Mumbai','TEST','QA',19,72.83),
 ('QADAD','QA Dadar Nearby','Mumbai','TEST','QA',19.02,72.84),
 ('QASRT','QA Surat','Surat','TEST','QA',21.17,72.83),
 ('QABRC','QA Vadodara Junction','Vadodara','TEST','QA',22.31,73.18),
 ('QAAHM','QA Ahmedabad','Ahmedabad','TEST','QA',23.03,72.58),
 ('QAMAN','QA Maninagar Nearby','Ahmedabad','TEST','QA',23.01,72.6),
 ('QAPUN','QA Pune','Pune','TEST','QA',18.53,73.87),
 ('QAZERO','QA Missing Coordinates','Test City','TEST','QA',NULL,NULL),
 ('QAWLS','QA Waitlist Origin','Test City','TEST','QA',28,78),
 ('QAWLD','QA Waitlist Destination','Test City','TEST','QA',28.9,78);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA101','QA Direct And Segment Express','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QAMUM' AND d.station_code='QABRC';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA101';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA101';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA101';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA101';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA101';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA101';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA101';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA101';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA101';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA101','QAMUM',1,NULL,'08:00:00',1,1,0),
 ('QA101','QASRT',2,'11:00:00','11:10:00',1,1,250),
 ('QA101','QABRC',3,'13:00:00',NULL,1,1,400);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA102','QA Valid Connection','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QABRC' AND d.station_code='QAAHM';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA102';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA102';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA102';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA102';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA102';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA102';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA102';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA102';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA102';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA102','QABRC',1,NULL,'14:30:00',1,1,0),
 ('QA102','QAAHM',2,'16:30:00',NULL,1,1,100);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA103','QA Too Short Connection','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QABRC' AND d.station_code='QAAHM';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA103';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA103';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA103';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA103';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA103';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA103';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA103';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA103';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA103';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA103','QABRC',1,NULL,'13:20:00',1,1,0),
 ('QA103','QAAHM',2,'15:20:00',NULL,1,1,100);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA104','QA Too Long Connection','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QABRC' AND d.station_code='QAAHM';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA104';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA104';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA104';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA104';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA104';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA104';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA104';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA104';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA104';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA104','QABRC',1,NULL,'21:30:00',1,1,0),
 ('QA104','QAAHM',2,'23:30:00',NULL,1,1,100);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA105','QA Nearby Direct Express','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QADAD' AND d.station_code='QAMAN';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA105';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA105';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA105';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA105';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA105';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA105';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA105';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA105';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA105';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA105','QADAD',1,NULL,'09:00:00',1,1,0),
 ('QA105','QAMAN',2,'17:00:00',NULL,1,1,480);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA106','QA Overnight First Leg','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QAMUM' AND d.station_code='QABRC';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA106';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA106';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA106';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA106';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA106';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA106';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA106';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA106';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA106';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA106','QAMUM',1,NULL,'22:00:00',1,1,0),
 ('QA106','QABRC',2,'02:00:00',NULL,2,2,400);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA107','QA Overnight Connection','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QABRC' AND d.station_code='QAAHM';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA107';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA107';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA107';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA107';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA107';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA107';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA107';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA107';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA107';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA107','QABRC',1,NULL,'03:00:00',1,1,0),
 ('QA107','QAAHM',2,'05:00:00',NULL,1,1,100);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA108','QA Monday Only Express','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QAMUM' AND d.station_code='QAPUN';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA108';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA108';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA108';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA108','QAMUM',1,NULL,'08:30:00',1,1,0),
 ('QA108','QAPUN',2,'11:30:00',NULL,1,1,150);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA109','QA Two Seat Waitlist Lab','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QAWLS' AND d.station_code='QAWLD';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA109';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,2,20,0.5,40 FROM trains WHERE train_number='QA109';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA109';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA109';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA109';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA109';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA109';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA109';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA109';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA109','QAWLS',1,NULL,'18:00:00',1,1,0),
 ('QA109','QAWLD',2,'19:30:00',NULL,1,1,100);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA110','QA Unpublished Train','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QAMUM' AND d.station_code='QAPUN';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA110';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA110';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA110';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA110';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA110';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA110';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA110';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA110';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA110';

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA111','QA No Coordinates Express','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QAZERO' AND d.station_code='QAAHM';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA111';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA111';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA111';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA111';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA111';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA111';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA111';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA111';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA111';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA111','QAZERO',1,NULL,'09:00:00',1,1,0),
 ('QA111','QAAHM',2,'10:00:00',NULL,1,1,100);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA112','QA Nearby Connection First Leg','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QADAD' AND d.station_code='QABRC';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA112';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA112';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA112';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA112';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA112';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA112';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA112';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA112';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA112';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA112','QADAD',1,NULL,'08:30:00',1,1,0),
 ('QA112','QABRC',2,'12:30:00',NULL,1,1,400);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA113','QA All Classes Layout Express','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QAMUM' AND d.station_code='QAPUN';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA113';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA113';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'CLASS_3A' FROM trains WHERE train_number='QA113';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'CLASS_3A',1,8,100,1,200 FROM trains WHERE train_number='QA113';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'CLASS_2A' FROM trains WHERE train_number='QA113';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'CLASS_2A',1,6,150,1.5,300 FROM trains WHERE train_number='QA113';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'CLASS_1A' FROM trains WHERE train_number='QA113';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'CLASS_1A',1,4,200,2,400 FROM trains WHERE train_number='QA113';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'CLASS_3E' FROM trains WHERE train_number='QA113';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'CLASS_3E',1,9,80,0.8,160 FROM trains WHERE train_number='QA113';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'EC' FROM trains WHERE train_number='QA113';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'EC',1,6,120,1.2,240 FROM trains WHERE train_number='QA113';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'CLASS_2S' FROM trains WHERE train_number='QA113';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'CLASS_2S',1,6,20,0.2,40 FROM trains WHERE train_number='QA113';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'UR' FROM trains WHERE train_number='QA113';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'UR',1,10,10,0.1,20 FROM trains WHERE train_number='QA113';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA113';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA113';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA113';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA113';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA113';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA113';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA113';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA113','QAMUM',1,NULL,'10:00:00',1,1,0),
 ('QA113','QAPUN',2,'14:00:00',NULL,1,1,200);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA114','QA Minimum Fare Express','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QAZERO' AND d.station_code='QAMAN';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA114';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,10,0.5,50 FROM trains WHERE train_number='QA114';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA114';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA114';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA114';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA114';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA114';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA114';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA114';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA114','QAZERO',1,NULL,'08:00:00',1,1,0),
 ('QA114','QAMAN',2,'09:00:00',NULL,1,1,10);

INSERT INTO trains (train_number,train_name,category,source_station_id,destination_station_id)
 SELECT 'QA115','QA Midnight Intermediate Stop','EXPRESS',s.station_id,d.station_id FROM stations s CROSS JOIN stations d WHERE s.station_code='QAMUM' AND d.station_code='QABRC';
INSERT INTO train_seat_classes (train_id,seat_class) SELECT train_id,'SL' FROM trains WHERE train_number='QA115';
INSERT INTO train_class_configurations (train_id,seat_class,coach_count,seats_per_coach,base_fare,rate_per_km,minimum_fare) SELECT train_id,'SL',1,8,50,0.5,100 FROM trains WHERE train_number='QA115';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'MONDAY' FROM trains WHERE train_number='QA115';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'TUESDAY' FROM trains WHERE train_number='QA115';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'WEDNESDAY' FROM trains WHERE train_number='QA115';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'THURSDAY' FROM trains WHERE train_number='QA115';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'FRIDAY' FROM trains WHERE train_number='QA115';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SATURDAY' FROM trains WHERE train_number='QA115';
INSERT INTO train_operating_days (train_id,operating_day) SELECT train_id,'SUNDAY' FROM trains WHERE train_number='QA115';
INSERT INTO railway_schedule_db.schedules (train_number,station_code,stop_sequence,arrival_time,departure_time,day_number,departure_day_number,distance_km) VALUES
 ('QA115','QAMUM',1,NULL,'21:00:00',1,1,0),
 ('QA115','QASRT',2,'23:55:00','00:05:00',1,2,250),
 ('QA115','QABRC',3,'02:00:00',NULL,2,2,400);

COMMIT;
END$$
DELIMITER ;
CALL railwize_load_qa_20261004();
-- Only the temporary helper procedure created above is removed; application records remain.
DROP PROCEDURE railwize_load_qa_20261004;

SELECT train_number,train_name FROM railway_train_db.trains WHERE train_number BETWEEN 'QA101' AND 'QA115' ORDER BY train_number;
SELECT train_number,COUNT(*) AS stops FROM railway_schedule_db.schedules WHERE train_number BETWEEN 'QA101' AND 'QA115' GROUP BY train_number ORDER BY train_number;
