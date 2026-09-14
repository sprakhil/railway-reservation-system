package com.railway.trainservice.service;

import com.railway.trainservice.dto.TrainDto;
import com.railway.trainservice.entity.Station;
import com.railway.trainservice.entity.Train;
import com.railway.trainservice.exception.ResourceNotFoundException;
import com.railway.trainservice.repository.StationRepository;
import com.railway.trainservice.repository.TrainRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrainServiceTest {

    @Mock private TrainRepository trainRepository;
    @Mock private StationRepository stationRepository;

    @InjectMocks private TrainService trainService;

    @Test
    void addTrain_Success() {
        TrainDto dto = TrainDto.builder()
                .trainNumber("12045")
                .sourceStationId(1L)
                .destinationStationId(2L)
                .build();

        Station src = Station.builder().stationId(1L).stationCode("A").build();
        Station dest = Station.builder().stationId(2L).stationCode("B").build();
        Train savedTrain = Train.builder().trainId(100L).trainNumber("12045").sourceStation(src).destinationStation(dest).build();

        when(trainRepository.existsByTrainNumber("12045")).thenReturn(false);
        when(stationRepository.findById(1L)).thenReturn(Optional.of(src));
        when(stationRepository.findById(2L)).thenReturn(Optional.of(dest));
        when(trainRepository.save(any(Train.class))).thenReturn(savedTrain);

        TrainDto result = trainService.addTrain(dto);

        assertNotNull(result);
        assertEquals("12045", result.getTrainNumber());
        assertEquals(1L, result.getSourceStationId());
        assertEquals(2L, result.getDestinationStationId());
    }

    @Test
    void addTrain_DuplicateTrainNumber_ThrowsException() {
        TrainDto dto = TrainDto.builder().trainNumber("12045").build();
        when(trainRepository.existsByTrainNumber("12045")).thenReturn(true);

        assertThrows(RuntimeException.class, () -> trainService.addTrain(dto));
        verify(trainRepository, never()).save(any());
    }

    @Test
    void getTrainByNumber_Success() {
        Station src = Station.builder().stationId(1L).build();
        Station dest = Station.builder().stationId(2L).build();
        Train mockTrain = Train.builder().trainNumber("12045").sourceStation(src).destinationStation(dest).build();

        when(trainRepository.findByTrainNumber("12045")).thenReturn(Optional.of(mockTrain));

        TrainDto result = trainService.getTrainByNumber("12045");
        assertNotNull(result);
        assertEquals("12045", result.getTrainNumber());
    }

    @Test
    void getTrainByNumber_NotFound_ThrowsException() {
        when(trainRepository.findByTrainNumber("99999")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> trainService.getTrainByNumber("99999"));
    }
}