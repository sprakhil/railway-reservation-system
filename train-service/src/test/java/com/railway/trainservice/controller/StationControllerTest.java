package com.railway.trainservice.controller;

import com.railway.trainservice.dto.StationDto;
import com.railway.trainservice.service.StationService;
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
class StationControllerTest {

    @Mock private StationService stationService;
    @InjectMocks private StationController stationController;

    @Test
    void getAllStations_ShouldReturn200() {
        when(stationService.getAllStations()).thenReturn(List.of(new StationDto(), new StationDto()));
        ResponseEntity<List<StationDto>> response = stationController.getAllStations();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void addStation_ShouldReturn201() {
        StationDto mockDto = StationDto.builder().stationCode("NDLS").build();
        when(stationService.addStation(any(StationDto.class))).thenReturn(mockDto);

        ResponseEntity<StationDto> response = stationController.addStation(new StationDto());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("NDLS", response.getBody().getStationCode());
    }
}