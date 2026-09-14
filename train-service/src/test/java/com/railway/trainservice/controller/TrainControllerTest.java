package com.railway.trainservice.controller;

import com.railway.trainservice.dto.TrainDto;
import com.railway.trainservice.service.TrainService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainControllerTest {

    @Mock private TrainService trainService;
    @InjectMocks private TrainController trainController;

    @Test
    void getTrain_ShouldReturn200() {
        TrainDto mockDto = TrainDto.builder().trainNumber("12045").build();
        when(trainService.getTrainByNumber("12045")).thenReturn(mockDto);

        ResponseEntity<TrainDto> response = trainController.getTrain("12045");
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("12045", response.getBody().getTrainNumber());
    }

    @Test
    void addTrain_ShouldReturn201() {
        TrainDto mockDto = TrainDto.builder().trainNumber("12045").build();
        when(trainService.addTrain(any(TrainDto.class))).thenReturn(mockDto);

        ResponseEntity<TrainDto> response = trainController.addTrain(new TrainDto());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("12045", response.getBody().getTrainNumber());
    }
}