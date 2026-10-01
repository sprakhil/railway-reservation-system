-- One-time MySQL 8 addition for an existing user-service database. Review/back up first.
CREATE TABLE saved_passengers (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(255) NOT NULL,name VARCHAR(100) NOT NULL,age INT NOT NULL,gender VARCHAR(10) NOT NULL,
 berth_preference VARCHAR(20),version BIGINT,
 INDEX idx_saved_passenger_owner(username)
);
