package com.railway.scheduleservice.service;

import com.railway.scheduleservice.dto.ScheduleDto;
import com.railway.scheduleservice.entity.Schedule;
import com.railway.scheduleservice.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private ScheduleRepository scheduleRepository;

    @InjectMocks
    private ScheduleService scheduleService;

    @Test
    void saveTrainSchedule_Success_DeletesOldAndSavesNew() {
        // Arrange
        String trainNumber = "12045";

        ScheduleDto stop1 = ScheduleDto.builder().stationCode("NDLS").stopSequence(1).build();
        ScheduleDto stop2 = ScheduleDto.builder().stationCode("CNB").stopSequence(2).build();
        List<ScheduleDto> inputDtos = List.of(stop1, stop2);

        Schedule savedStop1 = Schedule.builder().stationCode("NDLS").stopSequence(1).build();
        Schedule savedStop2 = Schedule.builder().stationCode("CNB").stopSequence(2).build();

        // Mock the delete (returns void, so we use doNothing)
        doNothing().when(scheduleRepository).deleteByTrainNumber(trainNumber);

        // Mock the bulk save
        when(scheduleRepository.saveAll(anyList())).thenReturn(List.of(savedStop1, savedStop2));

        // Act
        List<ScheduleDto> result = scheduleService.saveTrainSchedule(trainNumber, inputDtos);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("NDLS", result.get(0).getStationCode());

        // Verify it cleared the old schedule before saving the new one
        verify(scheduleRepository, times(1)).deleteByTrainNumber(trainNumber);
        verify(scheduleRepository, times(1)).saveAll(anyList());
    }

    @Test
    void getScheduleByTrainNumber_Success_ReturnsSortedList() {
        // Arrange
        String trainNumber = "12045";
        Schedule stop1 = Schedule.builder().stationCode("NDLS").stopSequence(1).build();
        Schedule stop2 = Schedule.builder().stationCode("CNB").stopSequence(2).build();

        when(scheduleRepository.findByTrainNumberOrderByStopSequenceAsc(trainNumber))
                .thenReturn(List.of(stop1, stop2));

        // Act
        List<ScheduleDto> result = scheduleService.getScheduleByTrainNumber(trainNumber);

        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1, result.get(0).getStopSequence());
        assertEquals(2, result.get(1).getStopSequence());
    }

    @Test
    void getScheduleByTrainNumber_NotFound_ThrowsException() {
        // Arrange
        when(scheduleRepository.findByTrainNumberOrderByStopSequenceAsc(anyString()))
                .thenReturn(new ArrayList<>()); // Returns empty list

        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            scheduleService.getScheduleByTrainNumber("99999");
        });

        assertTrue(exception.getMessage().contains("No schedule found"));
    }
}