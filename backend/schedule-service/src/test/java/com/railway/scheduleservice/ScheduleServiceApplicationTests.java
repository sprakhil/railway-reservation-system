package com.railway.scheduleservice;

import com.railway.scheduleservice.client.TrainCatalogClient;
import com.railway.scheduleservice.dto.ScheduleDto;
import com.railway.scheduleservice.exception.CatalogConflictException;
import com.railway.scheduleservice.repository.ScheduleRepository;
import com.railway.scheduleservice.service.ScheduleService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ScheduleServiceApplicationTests {
    @Autowired ScheduleService service;
    @Autowired ScheduleRepository repository;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @MockitoBean TrainCatalogClient catalog;

    @BeforeEach void catalog() {
        when(catalog.getTrain("12045")).thenReturn(new TrainCatalogClient.CatalogTrain("12045", 1L, 3L));
        when(catalog.getStation("NDLS")).thenReturn(new TrainCatalogClient.CatalogStation(1L, "NDLS"));
        when(catalog.getStation("CNB")).thenReturn(new TrainCatalogClient.CatalogStation(2L, "CNB"));
        when(catalog.getStation("LKO")).thenReturn(new TrainCatalogClient.CatalogStation(3L, "LKO"));
    }
    private List<ScheduleDto> route() {
        return new ArrayList<>(List.of(
                ScheduleDto.builder().stationCode("NDLS").stopSequence(1).dayNumber(1).distanceKm(0)
                        .departureTime(LocalTime.of(22, 0)).build(),
                ScheduleDto.builder().stationCode("CNB").stopSequence(2).dayNumber(1).departureDayNumber(2)
                        .distanceKm(400).arrivalTime(LocalTime.of(23, 55)).departureTime(LocalTime.of(0, 5)).build(),
                ScheduleDto.builder().stationCode("LKO").stopSequence(3).dayNumber(2).distanceKm(500)
                        .arrivalTime(LocalTime.of(3, 0)).build()));
    }
    @Test void midnightDepartureSurvivesPersistenceAndIdenticalPublicationDoesNotDuplicateStops() {
        service.saveTrainSchedule("12045", route()); em.flush(); em.clear();
        List<ScheduleDto> saved = service.getScheduleByTrainNumber("12045");
        assertEquals(3, saved.size());
        assertEquals(1, saved.getFirst().getDepartureDayNumber());
        assertEquals(2, saved.get(1).getDepartureDayNumber());
        assertEquals(LocalTime.of(0, 5), saved.get(1).getDepartureTime());
        assertEquals(400, saved.get(1).getDistanceKm());
        assertEquals(saved.getFirst().getId(), service.saveTrainSchedule("12045", route()).getFirst().getId());
        assertEquals(3, repository.count());
        List<ScheduleDto> changed = route(); changed.get(2).setDistanceKm(550);
        assertThrows(CatalogConflictException.class, () -> service.saveTrainSchedule("12045", changed));
        assertEquals(500, service.getScheduleByTrainNumber("12045").getLast().getDistanceKm());
    }
    @Test void invalidRouteDoesNotPartiallyPersist() {
        List<ScheduleDto> invalid = route(); invalid.get(1).setDepartureDayNumber(1);
        assertThrows(IllegalArgumentException.class, () -> service.saveTrainSchedule("12045", invalid));
        assertEquals(0, repository.count());
        verify(catalog, never()).getTrain(anyString());
    }
    @Test void onlyAdministratorCanPublishAndInvalidJsonGetsClientError() throws Exception {
        mvc.perform(get("/api/schedules/12045")).andExpect(status().isNotFound());
        mvc.perform(post("/api/schedules/12045").with(user("passenger").roles("USER"))
                .contentType("application/json").content("[]")).andExpect(status().isForbidden());
        mvc.perform(post("/api/schedules/12045").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content("[]")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/schedules/12045").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content("[{\"stationCode\":\"NDLS\",\"arrivalTime\":\"wrong\"}]"))
                .andExpect(status().isBadRequest());
    }
}
