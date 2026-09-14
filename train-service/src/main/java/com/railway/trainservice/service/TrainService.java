package com.railway.trainservice.service;

import com.railway.trainservice.dto.TrainDto;
import com.railway.trainservice.entity.Station;
import com.railway.trainservice.entity.Train;
import com.railway.trainservice.exception.ResourceNotFoundException;
import com.railway.trainservice.repository.StationRepository;
import com.railway.trainservice.repository.TrainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TrainService {

    private final TrainRepository trainRepository;
    private final StationRepository stationRepository;

    public TrainDto addTrain(TrainDto dto) {
        if (trainRepository.existsByTrainNumber(dto.getTrainNumber())) {
            throw new RuntimeException("Train number already exists");
        }

        Station source = stationRepository.findById(dto.getSourceStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Source station not found"));
        Station destination = stationRepository.findById(dto.getDestinationStationId())
                .orElseThrow(() -> new ResourceNotFoundException("Destination station not found"));

        Train train = Train.builder()
                .trainNumber(dto.getTrainNumber())
                .trainName(dto.getTrainName())
                .category(dto.getCategory())
                .sourceStation(source)
                .destinationStation(destination)
                .seatClasses(dto.getSeatClasses())
                .build();

        return mapToDto(trainRepository.save(train));
    }

    public List<TrainDto> getAllTrains() {
        return trainRepository.findAll().stream().map(this::mapToDto).collect(Collectors.toList());
    }

    public TrainDto getTrainByNumber(String trainNumber) {
        return mapToDto(trainRepository.findByTrainNumber(trainNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Train not found")));
    }

    private TrainDto mapToDto(Train train) {
        return TrainDto.builder()
                .trainId(train.getTrainId())
                .trainNumber(train.getTrainNumber())
                .trainName(train.getTrainName())
                .category(train.getCategory())
                .sourceStationId(train.getSourceStation().getStationId())
                .destinationStationId(train.getDestinationStation().getStationId())
                .seatClasses(train.getSeatClasses())
                .build();
    }
}