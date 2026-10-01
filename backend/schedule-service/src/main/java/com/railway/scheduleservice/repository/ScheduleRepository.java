package com.railway.scheduleservice.repository;

import com.railway.scheduleservice.entity.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    // Fetches the entire route for a train, ordered by its sequence
    List<Schedule> findByTrainNumberOrderByStopSequenceAsc(String trainNumber);

    @Transactional
    void deleteByTrainNumber(String trainNumber);
}