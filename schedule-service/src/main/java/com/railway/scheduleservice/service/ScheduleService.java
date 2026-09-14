package com.railway.scheduleservice.service;

import com.railway.scheduleservice.dto.ScheduleDto;
import com.railway.scheduleservice.entity.Schedule;
import com.railway.scheduleservice.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;

    // Admin creates or completely replaces a route for a train
    @Transactional
    public List<ScheduleDto> saveTrainSchedule(String trainNumber, List<ScheduleDto> scheduleDtos) {
        // Clear old schedule if it exists
        scheduleRepository.deleteByTrainNumber(trainNumber);

        List<Schedule> schedules = scheduleDtos.stream().map(dto -> Schedule.builder()
                .trainNumber(trainNumber) // Enforce the path variable
                .stationCode(dto.getStationCode())
                .stopSequence(dto.getStopSequence())
                .arrivalTime(dto.getArrivalTime())
                .departureTime(dto.getDepartureTime())
                .dayNumber(dto.getDayNumber())
                .distanceKm(dto.getDistanceKm())
                .build()).collect(Collectors.toList());

        List<Schedule> savedSchedules = scheduleRepository.saveAll(schedules);
        log.info("Saved {} stops for train {}", savedSchedules.size(), trainNumber);

        return savedSchedules.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ScheduleDto> getScheduleByTrainNumber(String trainNumber) {
        List<Schedule> schedules = scheduleRepository.findByTrainNumberOrderByStopSequenceAsc(trainNumber);
        if (schedules.isEmpty()) {
            throw new RuntimeException("No schedule found for train: " + trainNumber);
        }
        return schedules.stream().map(this::mapToDto).collect(Collectors.toList());
    }

    private ScheduleDto mapToDto(Schedule schedule) {
        return ScheduleDto.builder()
                .id(schedule.getId())
                .trainNumber(schedule.getTrainNumber())
                .stationCode(schedule.getStationCode())
                .stopSequence(schedule.getStopSequence())
                .arrivalTime(schedule.getArrivalTime())
                .departureTime(schedule.getDepartureTime())
                .dayNumber(schedule.getDayNumber())
                .distanceKm(schedule.getDistanceKm())
                .build();
    }
}