package com.railway.trainservice.service;

import com.railway.trainservice.dto.StationDto;
import com.railway.trainservice.entity.Station;
import com.railway.trainservice.exception.ResourceNotFoundException;
import com.railway.trainservice.repository.StationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StationServiceTest {

    @Mock
    private StationRepository stationRepository;

    @InjectMocks
    private StationService stationService;

    @Test
    void addStation_Success() {
        StationDto dto = StationDto.builder().stationCode("NDLS").stationName("New Delhi").build();
        Station savedStation = Station.builder().stationId(1L).stationCode("NDLS").stationName("New Delhi").build();

        when(stationRepository.existsByStationCode("NDLS")).thenReturn(false);
        when(stationRepository.save(any(Station.class))).thenReturn(savedStation);

        StationDto result = stationService.addStation(dto);

        assertNotNull(result);
        assertEquals("NDLS", result.getStationCode());
        assertEquals(1L, result.getStationId());
        verify(stationRepository, times(1)).save(any(Station.class));
    }

    @Test
    void addStation_DuplicateCode_ThrowsException() {
        StationDto dto = StationDto.builder().stationCode("NDLS").build();
        when(stationRepository.existsByStationCode("NDLS")).thenReturn(true);

        Exception exception = assertThrows(RuntimeException.class, () -> stationService.addStation(dto));
        assertTrue(exception.getMessage().contains("already exists"));
        verify(stationRepository, never()).save(any());
    }

    @Test
    void getStationById_Success() {
        Station mockStation = Station.builder().stationId(1L).stationCode("NDLS").build();
        when(stationRepository.findById(1L)).thenReturn(Optional.of(mockStation));

        StationDto result = stationService.getStationById(1L);

        assertNotNull(result);
        assertEquals("NDLS", result.getStationCode());
    }

    @Test
    void getStationById_NotFound_ThrowsException() {
        when(stationRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> stationService.getStationById(99L));
    }
}