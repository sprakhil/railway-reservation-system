package com.railway.trainservice;

import com.railway.trainservice.dto.*;
import com.railway.trainservice.entity.*;
import com.railway.trainservice.exception.CatalogConflictException;
import com.railway.trainservice.repository.*;
import com.railway.trainservice.service.TrainService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TrainServiceApplicationTests {
    @Autowired TrainService service;
    @Autowired StationRepository stations;
    @Autowired TrainRepository trains;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;

    private TrainDto configuredTrain() {
        Station source = stations.saveAndFlush(Station.builder().stationCode("NDLS").stationName("Delhi").build());
        Station destination = stations.saveAndFlush(Station.builder().stationCode("LKO").stationName("Lucknow").build());
        return TrainDto.builder().trainNumber("12045").trainName("Demo Express").category(TrainCategory.EXPRESS)
                .sourceStationId(source.getStationId()).destinationStationId(destination.getStationId())
                .seatClasses(Set.of(SeatClass.SL, SeatClass.CLASS_3A))
                .operatingDays(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
                .classConfigurations(List.of(config(SeatClass.SL, "0.50"), config(SeatClass.CLASS_3A, "1.20"))).build();
    }
    private TrainClassConfigDto config(SeatClass seatClass, String rate) {
        return TrainClassConfigDto.builder().seatClass(seatClass).coachCount(2).seatsPerCoach(72)
                .baseFare(new BigDecimal("20.00")).minimumFare(new BigDecimal("40.00"))
                .ratePerKm(new BigDecimal(rate)).build();
    }
    @Test void roundTripsMultipleClassesAndDaysWithoutDuplicatingInventory() {
        service.addTrain(configuredTrain()); em.flush(); em.clear();
        TrainDto saved = service.getTrainByNumber("12045");
        assertEquals(2, saved.getClassConfigurations().size());
        assertEquals(Set.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), saved.getOperatingDays());
        assertEquals(new BigDecimal("0.50"), saved.getClassConfigurations().stream()
                .filter(c -> c.getSeatClass() == SeatClass.SL).findFirst().orElseThrow().getRatePerKm());
        em.clear();
        assertEquals(2, service.getAllTrains().getFirst().getClassConfigurations().size());
        saved.getClassConfigurations().forEach(c -> c.setRatePerKm(new BigDecimal("0.75")));
        service.updateTrain("12045", saved); em.flush(); em.clear();
        TrainDto updated = service.getTrainByNumber("12045");
        assertEquals(2, updated.getClassConfigurations().size());
        assertTrue(updated.getClassConfigurations().stream().allMatch(c -> c.getRatePerKm().equals(new BigDecimal("0.75"))));
        updated.getClassConfigurations().getFirst().setSeatsPerCoach(71);
        assertThrows(CatalogConflictException.class, () -> service.updateTrain("12045", updated));
    }
    @Test void legacyTrainRequiresExplicitConfigurationBeforeInventoryIsPublished() {
        TrainDto dto = configuredTrain();
        trains.saveAndFlush(Train.builder().trainNumber(dto.getTrainNumber()).trainName(dto.getTrainName())
                .category(dto.getCategory()).sourceStation(stations.findById(dto.getSourceStationId()).orElseThrow())
                .destinationStation(stations.findById(dto.getDestinationStationId()).orElseThrow())
                .seatClasses(dto.getSeatClasses()).build());
        em.clear();
        TrainDto legacy = service.getTrainByNumber("12045");
        assertTrue(legacy.getClassConfigurations().isEmpty());
        assertTrue(legacy.getOperatingDays().isEmpty());
        service.updateTrain("12045", dto); em.flush(); em.clear();
        assertEquals(2, service.getTrainByNumber("12045").getClassConfigurations().size());
    }
    @Test void publicCatalogAndAdministratorMutationRulesAreEnforced() throws Exception {
        mvc.perform(get("/api/trains")).andExpect(status().isOk());
        mvc.perform(post("/api/stations").with(user("passenger").roles("USER"))
                .contentType("application/json").content("{\"stationCode\":\"TEST\",\"stationName\":\"Test Station\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/stations").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content("{\"stationCode\":\"TEST\",\"stationName\":\"Test Station\"}"))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/stations").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content("{\"stationCode\":\"TEST\",\"stationName\":\"Duplicate\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/trains").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content("{\"trainNumber\":\"INVALID!\"}"))
                .andExpect(status().isBadRequest());
    }
}
