package com.railway.scheduleservice.service;

import com.railway.scheduleservice.client.TrainCatalogClient;
import com.railway.scheduleservice.dto.ScheduleDto;
import com.railway.scheduleservice.entity.Schedule;
import com.railway.scheduleservice.exception.*;
import com.railway.scheduleservice.repository.ScheduleRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalTime;
import java.util.*;

@Service @RequiredArgsConstructor
public class ScheduleService {
    private final ScheduleRepository scheduleRepository;
    private final TrainCatalogClient trainCatalogClient;

    @Transactional
    public List<ScheduleDto> saveTrainSchedule(String trainNumber, List<ScheduleDto> scheduleDtos) {
        if (scheduleDtos != null) scheduleDtos.stream().filter(Objects::nonNull)
                .forEach(stop -> stop.setStationCode(stop.getStationCode()));
        validateRoute(trainNumber, scheduleDtos);
        List<Schedule> existing = scheduleRepository.findByTrainNumberOrderByStopSequenceAsc(trainNumber);
        if (!existing.isEmpty()) {
            if (sameRoute(existing, scheduleDtos)) return existing.stream().map(this::mapToDto).toList();
            // A remote booking check alone races with concurrent bookings. Keep published routes immutable.
            throw new CatalogConflictException("Published timetables are immutable; create a new train service for route or time changes");
        }
        validateCatalog(trainNumber, scheduleDtos);
        List<Schedule> schedules = scheduleDtos.stream().map(dto -> Schedule.builder()
                .trainNumber(trainNumber).stationCode(dto.getStationCode()).stopSequence(dto.getStopSequence())
                .arrivalTime(dto.getArrivalTime()).departureTime(dto.getDepartureTime())
                .dayNumber(dto.getDayNumber()).departureDayNumber(departureDay(dto))
                .distanceKm(dto.getDistanceKm()).build()).toList();
        return scheduleRepository.saveAllAndFlush(schedules).stream().map(this::mapToDto).toList();
    }

    private void validateRoute(String number, List<ScheduleDto> stops) {
        if (number == null || !number.matches("[A-Z0-9]{1,10}"))
            throw new ScheduleValidationException("Train number must contain 1-10 uppercase letters or digits");
        if (stops == null || stops.size() < 2 || stops.size() > 200)
            throw new ScheduleValidationException("A timetable requires 2-200 ordered stops");
        Set<String> codes = new HashSet<>();
        long previousDeparture = -1;
        int previousDistance = -1;
        for (int i = 0; i < stops.size(); i++) {
            ScheduleDto s = stops.get(i);
            if (s == null || s.getStationCode() == null || !s.getStationCode().matches("[A-Z0-9]{1,10}") || !codes.add(s.getStationCode()))
                throw new ScheduleValidationException("Each stop must reference a unique valid station code");
            if (s.getTrainNumber() != null && !s.getTrainNumber().equals(number))
                throw new ScheduleValidationException("Stop train number must match the path");
            if (s.getStopSequence() == null || s.getStopSequence() != i + 1)
                throw new ScheduleValidationException("Stops must be submitted in consecutive order beginning at 1");
            if (s.getDayNumber() == null || s.getDayNumber() < 1 || s.getDayNumber() > 30)
                throw new ScheduleValidationException("Day number must be 1-30");
            if (departureDay(s) < s.getDayNumber() || departureDay(s) > 30)
                throw new ScheduleValidationException("Departure day must be between the arrival day and day 30");
            if (s.getDistanceKm() == null || s.getDistanceKm() < 0 || s.getDistanceKm() > 20000
                    || s.getDistanceKm() <= previousDistance)
                throw new ScheduleValidationException("Cumulative distances must strictly increase and be at most 20000 km");
            if (i == 0 && (s.getDistanceKm() != 0 || s.getDayNumber() != 1 || departureDay(s) != 1 || s.getArrivalTime() != null))
                throw new ScheduleValidationException("Source must begin at distance 0 on day 1 with no arrival time");
            if (i == stops.size() - 1 && s.getDepartureTime() != null)
                throw new ScheduleValidationException("Destination must have no departure time");
            if ((i > 0 && s.getArrivalTime() == null) || (i < stops.size() - 1 && s.getDepartureTime() == null))
                throw new ScheduleValidationException("Arrival and departure times are required except at route endpoints");
            if ((s.getArrivalTime() != null && s.getArrivalTime().getNano() != 0)
                    || (s.getDepartureTime() != null && s.getDepartureTime().getNano() != 0))
                throw new ScheduleValidationException("Timetable times must use whole seconds");
            long arrival = absoluteSeconds(s.getDayNumber(), s.getArrivalTime());
            long departure = absoluteSeconds(departureDay(s), s.getDepartureTime());
            if (i > 0 && arrival <= previousDeparture)
                throw new ScheduleValidationException("Each arrival must follow the previous stop's departure");
            if (s.getArrivalTime() != null && s.getDepartureTime() != null && departure < arrival)
                throw new ScheduleValidationException("Departure cannot precede arrival at a stop");
            previousDeparture = departure;
            previousDistance = s.getDistanceKm();
        }
    }

    private void validateCatalog(String number, List<ScheduleDto> stops) {
        try {
            TrainCatalogClient.CatalogTrain train = trainCatalogClient.getTrain(number);
            if (train == null || train.sourceStationId() == null || train.destinationStationId() == null)
                throw new CatalogUnavailableException("Train catalog is temporarily unavailable");
            for (int i = 0; i < stops.size(); i++) {
                TrainCatalogClient.CatalogStation station = trainCatalogClient.getStation(stops.get(i).getStationCode());
                if (station == null || station.stationId() == null)
                    throw new CatalogUnavailableException("Station catalog is temporarily unavailable");
                if ((i == 0 && !station.stationId().equals(train.sourceStationId()))
                        || (i == stops.size() - 1 && !station.stationId().equals(train.destinationStationId())))
                    throw new ScheduleValidationException("Timetable endpoints must match the train's source and destination");
            }
        } catch (FeignException.NotFound ex) {
            throw new ResourceNotFoundException("Train or station does not exist in the catalog");
        } catch (FeignException ex) {
            throw new CatalogUnavailableException("Train catalog is temporarily unavailable; timetable was not saved");
        }
    }

    private long absoluteSeconds(int day, LocalTime time) {
        return time == null ? -1 : (day - 1L) * 86400 + time.toSecondOfDay();
    }
    private int departureDay(ScheduleDto s) {
        return s.getDepartureDayNumber() == null ? s.getDayNumber() : s.getDepartureDayNumber();
    }
    private boolean sameRoute(List<Schedule> existing, List<ScheduleDto> proposed) {
        if (existing.size() != proposed.size()) return false;
        for (int i = 0; i < existing.size(); i++) {
            Schedule e = existing.get(i); ScheduleDto p = proposed.get(i);
            if (!Objects.equals(e.getStationCode(), p.getStationCode()) || !Objects.equals(e.getStopSequence(), p.getStopSequence())
                    || !Objects.equals(e.getArrivalTime(), p.getArrivalTime()) || !Objects.equals(e.getDepartureTime(), p.getDepartureTime())
                    || (e.getDepartureDayNumber() == null ? e.getDayNumber() : e.getDepartureDayNumber()) != departureDay(p)
                    || !Objects.equals(e.getDayNumber(), p.getDayNumber()) || !Objects.equals(e.getDistanceKm(), p.getDistanceKm())) return false;
        }
        return true;
    }
    @Transactional(readOnly = true)
    public List<ScheduleDto> getScheduleByTrainNumber(String trainNumber) {
        List<Schedule> schedules = scheduleRepository.findByTrainNumberOrderByStopSequenceAsc(trainNumber);
        if (schedules.isEmpty()) throw new ResourceNotFoundException("No schedule found for train: " + trainNumber);
        return schedules.stream().map(this::mapToDto).toList();
    }
    private ScheduleDto mapToDto(Schedule s) {
        return ScheduleDto.builder().id(s.getId()).trainNumber(s.getTrainNumber()).stationCode(s.getStationCode())
                .stopSequence(s.getStopSequence()).arrivalTime(s.getArrivalTime()).departureTime(s.getDepartureTime())
                .dayNumber(s.getDayNumber())
                .departureDayNumber(s.getDepartureDayNumber() == null ? s.getDayNumber() : s.getDepartureDayNumber())
                .distanceKm(s.getDistanceKm()).build();
    }
}
