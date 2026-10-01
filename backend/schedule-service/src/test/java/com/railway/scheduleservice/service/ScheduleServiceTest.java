package com.railway.scheduleservice.service;

import com.railway.scheduleservice.client.TrainCatalogClient;
import com.railway.scheduleservice.dto.ScheduleDto;
import com.railway.scheduleservice.entity.Schedule;
import com.railway.scheduleservice.exception.*;
import com.railway.scheduleservice.repository.ScheduleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {
    @Mock ScheduleRepository repository;
    @Mock TrainCatalogClient catalog;
    @InjectMocks ScheduleService service;
    private List<ScheduleDto> route() {
        return new ArrayList<>(List.of(
                ScheduleDto.builder().stationCode("NDLS").stopSequence(1).dayNumber(1).distanceKm(0)
                        .departureTime(LocalTime.of(22, 0)).build(),
                ScheduleDto.builder().stationCode("CNB").stopSequence(2).dayNumber(2).distanceKm(400)
                        .arrivalTime(LocalTime.of(3, 0)).departureTime(LocalTime.of(3, 10)).build(),
                ScheduleDto.builder().stationCode("LKO").stopSequence(3).dayNumber(2).distanceKm(500)
                        .arrivalTime(LocalTime.of(5, 0)).build()));
    }
    private void validCatalog() {
        when(catalog.getTrain("12045")).thenReturn(new TrainCatalogClient.CatalogTrain("12045", 1L, 3L));
        when(catalog.getStation("NDLS")).thenReturn(new TrainCatalogClient.CatalogStation(1L, "NDLS"));
        when(catalog.getStation("CNB")).thenReturn(new TrainCatalogClient.CatalogStation(2L, "CNB"));
        when(catalog.getStation("LKO")).thenReturn(new TrainCatalogClient.CatalogStation(3L, "LKO"));
        when(repository.saveAllAndFlush(anyList())).thenAnswer(i -> i.getArgument(0));
    }
    private List<Schedule> persisted(List<ScheduleDto> list) {
        return list.stream().map(d -> Schedule.builder().trainNumber("12045").stationCode(d.getStationCode())
                .stopSequence(d.getStopSequence()).dayNumber(d.getDayNumber()).departureDayNumber(d.getDepartureDayNumber())
                .arrivalTime(d.getArrivalTime()).departureTime(d.getDepartureTime()).distanceKm(d.getDistanceKm()).build()).toList();
    }
    @Test void validatesAndSavesOvernightRouteWithoutDeleting() {
        validCatalog();
        List<ScheduleDto> result = service.saveTrainSchedule("12045", route());
        assertEquals(3, result.size()); assertEquals(2, result.get(1).getDepartureDayNumber());
        verify(repository, never()).deleteByTrainNumber(anyString());
    }
    @Test void supportsStopStraddlingMidnight() {
        validCatalog(); List<ScheduleDto> route = route();
        route.get(1).setDayNumber(1); route.get(1).setArrivalTime(LocalTime.of(23, 55));
        route.get(1).setDepartureDayNumber(2); route.get(1).setDepartureTime(LocalTime.of(0, 5));
        assertEquals(2, service.saveTrainSchedule("12045", route).get(1).getDepartureDayNumber());
    }
    @Test void rejectsEmptyRouteDuplicateAndUnorderedStops() {
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", List.of()));
        List<ScheduleDto> route = route(); route.get(1).setStationCode("NDLS");
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", route));
        route.get(1).setStationCode("CNB"); route.get(1).setStopSequence(3);
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", route));
        verifyNoInteractions(catalog);
    }
    @Test void rejectsNonIncreasingAndMissingDistance() {
        List<ScheduleDto> route = route(); route.get(2).setDistanceKm(400);
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", route));
        route.get(2).setDistanceKm(null);
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", route));
    }
    @Test void rejectsImpossibleTimesAndMissingDeparture() {
        List<ScheduleDto> route = route(); route.get(1).setDayNumber(1);
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", route));
        route.get(1).setDayNumber(2); route.get(1).setDepartureTime(LocalTime.of(2, 59));
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", route));
        route.get(1).setDepartureTime(null);
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", route));
    }
    @Test void rejectsWrongTrainAndSourceOffset() {
        List<ScheduleDto> route = route(); route.getFirst().setTrainNumber("99999");
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", route));
        route.getFirst().setTrainNumber(null); route.getFirst().setDistanceKm(1);
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", route));
    }
    @Test void rejectsEndpointsThatDisagreeWithCatalog() {
        when(catalog.getTrain("12045")).thenReturn(new TrainCatalogClient.CatalogTrain("12045", 9L, 3L));
        when(catalog.getStation("NDLS")).thenReturn(new TrainCatalogClient.CatalogStation(1L, "NDLS"));
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", route()));
        verify(repository, never()).saveAllAndFlush(anyList());
    }
    @Test void rejectsUnavailableCatalogWithoutSaving() {
        assertThrows(CatalogUnavailableException.class, () -> service.saveTrainSchedule("12045", route()));
        verify(repository, never()).saveAllAndFlush(anyList());
    }
    @Test void cannotReplacePublishedRoute() {
        when(repository.findByTrainNumberOrderByStopSequenceAsc("12045")).thenReturn(persisted(route()));
        List<ScheduleDto> next = route(); next.get(2).setDistanceKm(510);
        assertThrows(CatalogConflictException.class, () -> service.saveTrainSchedule("12045", next));
        verify(repository, never()).saveAllAndFlush(anyList());
        verifyNoInteractions(catalog);
    }
    @Test void repeatedIdenticalRouteIsIdempotent() {
        when(repository.findByTrainNumberOrderByStopSequenceAsc("12045")).thenReturn(persisted(route()));
        assertEquals(3, service.saveTrainSchedule("12045", route()).size());
        verify(repository, never()).saveAllAndFlush(anyList());
    }
    @Test void missingTimetableIsNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> service.getScheduleByTrainNumber("99999"));
    }
}
