package com.railway.scheduleservice.controller;

import com.railway.scheduleservice.dto.ScheduleDto;
import com.railway.scheduleservice.service.ScheduleService;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleControllerTest {

    @Mock
    private ScheduleService scheduleService;

    @InjectMocks
    private ScheduleController scheduleController;

    @Test
    void getSchedule_ShouldReturn200AndList() {
        // Arrange
        String trainNumber = "12045";
        List<ScheduleDto> mockList = List.of(new ScheduleDto(), new ScheduleDto());

        when(scheduleService.getScheduleByTrainNumber(trainNumber)).thenReturn(mockList);

        // Act
        ResponseEntity<List<ScheduleDto>> response = scheduleController.getSchedule(trainNumber);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void addSchedule_ShouldReturn201AndList() {
        // Arrange
        String trainNumber = "12045";
        List<ScheduleDto> inputList = List.of(new ScheduleDto());
        List<ScheduleDto> outputList = List.of(new ScheduleDto());

        when(scheduleService.saveTrainSchedule(eq(trainNumber), anyList())).thenReturn(outputList);

        // Act
        ResponseEntity<List<ScheduleDto>> response = scheduleController.addSchedule(trainNumber, inputList);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
    }
}