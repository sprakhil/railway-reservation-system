package com.railway.trainservice.service;

import com.railway.trainservice.dto.*;
import com.railway.trainservice.entity.*;
import com.railway.trainservice.exception.*;
import com.railway.trainservice.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class TrainService {
    private final TrainRepository trainRepository;
    private final StationRepository stationRepository;

    @Transactional
    public TrainDto addTrain(TrainDto dto) {
        if (trainRepository.existsByTrainNumber(dto.getTrainNumber()))
            throw new CatalogConflictException("Train number already exists");
        validate(dto);
        Station source = stationRepository.findById(dto.getSourceStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Source station not found"));
        Station destination = stationRepository.findById(dto.getDestinationStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination station not found"));
        Train train = Train.builder().trainNumber(dto.getTrainNumber())
                .sourceStation(source).destinationStation(destination).build();
        applyEditableFields(train, dto);
        return mapToDto(trainRepository.save(train));
    }

    @Transactional
    public TrainDto updateTrain(String trainNumber, TrainDto dto) {
        Train train = trainRepository.findForUpdate(trainNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Train not found"));
        validate(dto);
        // Timetables and historical bookings refer to these stable identifiers.
        if (!trainNumber.equals(dto.getTrainNumber())
                || !train.getSourceStation().getStationId().equals(dto.getSourceStationId())
                || !train.getDestinationStation().getStationId().equals(dto.getDestinationStationId()))
            throw new CatalogConflictException("Train number and route endpoints cannot be changed; create a new train service");
        List<TrainClassConfiguration> existing = configurations(train);
        if (!existing.isEmpty()) {
            if (!Objects.equals(train.getOperatingDays(), dto.getOperatingDays())
                    || !Objects.equals(train.getSeatClasses(), dto.getSeatClasses())
                    || !sameCapacity(existing, dto.getClassConfigurations()))
                throw new CatalogConflictException("Published operating days and seat inventory are immutable; create a new train service");
        }
        applyEditableFields(train, dto);
        return mapToDto(trainRepository.save(train));
    }

    private boolean sameCapacity(List<TrainClassConfiguration> old, List<TrainClassConfigDto> next) {
        if (next == null || old.size() != next.size()) return false;
        Map<SeatClass, TrainClassConfigDto> byClass = next.stream()
                .collect(Collectors.toMap(TrainClassConfigDto::getSeatClass, c -> c));
        return old.stream().allMatch(c -> {
            TrainClassConfigDto n = byClass.get(c.getSeatClass());
            return n != null && c.getCoachCount() == n.getCoachCount() && c.getSeatsPerCoach() == n.getSeatsPerCoach();
        });
    }

    private void validate(TrainDto dto) {
        if (dto.getTrainNumber() == null || !dto.getTrainNumber().matches("[A-Z0-9]{1,10}"))
            throw new IllegalArgumentException("Train number must contain 1-10 uppercase letters or digits");
        if (dto.getTrainName() == null || dto.getTrainName().isBlank() || dto.getTrainName().length() > 255 || dto.getCategory() == null)
            throw new IllegalArgumentException("Train name (at most 255 characters) and category are required");
        if (dto.getSourceStationId() == null || dto.getDestinationStationId() == null
                || dto.getSourceStationId() <= 0 || dto.getDestinationStationId() <= 0
                || dto.getSourceStationId().equals(dto.getDestinationStationId()))
            throw new IllegalArgumentException("Source and destination must be different valid stations");
        if (dto.getSeatClasses() == null || dto.getSeatClasses().isEmpty() || dto.getSeatClasses().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("At least one valid seat class is required");
        if (dto.getOperatingDays() == null || dto.getOperatingDays().isEmpty() || dto.getOperatingDays().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("At least one valid operating day is required");
        Set<SeatClass> seen = new HashSet<>();
        for (TrainClassConfigDto c : dto.getClassConfigurations() == null ? List.<TrainClassConfigDto>of() : dto.getClassConfigurations()) {
            if (c == null || c.getSeatClass() == null || !dto.getSeatClasses().contains(c.getSeatClass()) || !seen.add(c.getSeatClass()))
                throw new IllegalArgumentException("Each class configuration must have a unique supported seat class");
            if (c.getCoachCount() < 1 || c.getCoachCount() > 30 || c.getSeatsPerCoach() < 1 || c.getSeatsPerCoach() > 100)
                throw new IllegalArgumentException("Configure 1-30 coaches and 1-100 seats per coach");
            validateMoney(c.getBaseFare()); validateMoney(c.getRatePerKm()); validateMoney(c.getMinimumFare());
        }
        if (!seen.isEmpty() && !seen.equals(dto.getSeatClasses()))
            throw new IllegalArgumentException("Configure every declared seat class together, or leave all inventory unconfigured");
    }

    private void validateMoney(BigDecimal money) {
        if (money == null || money.signum() < 0 || money.compareTo(new BigDecimal("9999999.99")) > 0
                || money.stripTrailingZeros().scale() > 2)
            throw new IllegalArgumentException("Demo fare amounts must be nonnegative INR with at most two decimal places");
    }

    private void applyEditableFields(Train train, TrainDto dto) {
        train.setTrainName(dto.getTrainName().trim()); train.setCategory(dto.getCategory());
        train.setSeatClasses(new HashSet<>(dto.getSeatClasses()));
        train.setOperatingDays(new HashSet<>(dto.getOperatingDays()));
        train.setClassConfigurations((dto.getClassConfigurations() == null ? List.<TrainClassConfigDto>of() : dto.getClassConfigurations())
                .stream().map(c -> TrainClassConfiguration.builder().seatClass(c.getSeatClass())
                        .coachCount(c.getCoachCount()).seatsPerCoach(c.getSeatsPerCoach())
                        .baseFare(c.getBaseFare().setScale(2, RoundingMode.UNNECESSARY))
                        .ratePerKm(c.getRatePerKm().setScale(2, RoundingMode.UNNECESSARY))
                        .minimumFare(c.getMinimumFare().setScale(2, RoundingMode.UNNECESSARY)).build())
                .collect(Collectors.toCollection(ArrayList::new)));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly=true)
    public com.railway.trainservice.dto.PageResult<TrainDto> page(int page,int size,String sort,String direction) {
        return com.railway.trainservice.dto.PageResult.of(trainRepository.findAll(com.railway.trainservice.config.Paging.of(page,size,sort,direction,
            java.util.Set.of("trainId","trainName","trainNumber"),"trainId")).map(this::mapToDto));
    }
    public List<TrainDto> getAllTrains() {
        return trainRepository.findAll().stream().map(this::mapToDto).toList();
    }
    public TrainDto getTrainByNumber(String trainNumber) {
        return mapToDto(trainRepository.findByTrainNumber(trainNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Train not found")));
    }
    private List<TrainClassConfiguration> configurations(Train train) {
        return train.getClassConfigurations() == null ? List.of() : train.getClassConfigurations();
    }
    private TrainDto mapToDto(Train train) {
        return TrainDto.builder().trainId(train.getTrainId()).trainNumber(train.getTrainNumber())
                .trainName(train.getTrainName()).category(train.getCategory())
                .sourceStationId(train.getSourceStation().getStationId())
                .destinationStationId(train.getDestinationStation().getStationId())
                .seatClasses(train.getSeatClasses() == null ? Set.of() : train.getSeatClasses())
                .operatingDays(train.getOperatingDays() == null ? Set.of() : train.getOperatingDays())
                .classConfigurations(configurations(train).stream().map(c -> TrainClassConfigDto.builder()
                        .seatClass(c.getSeatClass()).coachCount(c.getCoachCount()).seatsPerCoach(c.getSeatsPerCoach())
                        .baseFare(c.getBaseFare()).ratePerKm(c.getRatePerKm()).minimumFare(c.getMinimumFare()).build()).toList())
                .build();
    }
}
