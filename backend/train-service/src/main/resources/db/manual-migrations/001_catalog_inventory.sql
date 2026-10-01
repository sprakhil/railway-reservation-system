-- MySQL 8; execute against the train-service database before deploying the new service.
-- These tables deliberately remain empty for existing trains: administrators must
-- explicitly configure operating days, capacity and sample fares before booking.
CREATE TABLE IF NOT EXISTS train_operating_days (
    train_id BIGINT NOT NULL,
    operating_day ENUM('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY') NOT NULL,
    PRIMARY KEY (train_id, operating_day),
    CONSTRAINT fk_operating_days_train FOREIGN KEY (train_id) REFERENCES trains (train_id)
);

CREATE TABLE IF NOT EXISTS train_class_configurations (
    train_id BIGINT NOT NULL,
    seat_class ENUM('CLASS_1A','CLASS_2A','CLASS_3A','CLASS_3E','EC','SL','CLASS_2S','UR') NOT NULL,
    coach_count INT NOT NULL,
    seats_per_coach INT NOT NULL,
    base_fare DECIMAL(9,2) NOT NULL,
    rate_per_km DECIMAL(9,2) NOT NULL,
    minimum_fare DECIMAL(9,2) NOT NULL,
    PRIMARY KEY (train_id, seat_class),
    CONSTRAINT fk_class_config_train FOREIGN KEY (train_id) REFERENCES trains (train_id),
    CONSTRAINT ck_coach_capacity CHECK (coach_count BETWEEN 1 AND 30 AND seats_per_coach BETWEEN 1 AND 100),
    CONSTRAINT ck_nonnegative_fares CHECK (base_fare >= 0 AND rate_per_km >= 0 AND minimum_fare >= 0)
);
