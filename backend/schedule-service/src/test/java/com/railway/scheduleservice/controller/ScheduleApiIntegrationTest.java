package com.railway.scheduleservice.controller;

import com.railway.scheduleservice.client.TrainCatalogClient;
import com.railway.scheduleservice.repository.ScheduleRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:schedule-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class ScheduleApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ScheduleRepository schedules;
    @MockitoBean TrainCatalogClient catalogue;
    private static final String ROUTE="""
        [{"stationCode":" aaa ","stopSequence":1,"distanceKm":0,"dayNumber":1,"departureTime":"10:00:00"},
         {"stationCode":" cCc ","stopSequence":2,"distanceKm":200,"dayNumber":1,"arrivalTime":"12:00:00"}]
        """;
    @BeforeEach void setup() {
        schedules.deleteAll();
        when(catalogue.getTrain("12345")).thenReturn(new TrainCatalogClient.CatalogTrain("12345",1L,2L));
        when(catalogue.getStation("AAA")).thenReturn(new TrainCatalogClient.CatalogStation(1L,"AAA"));
        when(catalogue.getStation("CCC")).thenReturn(new TrainCatalogClient.CatalogStation(2L,"CCC"));
    }
    @Test void publishesAndReplaysNormalizedTimetableWithoutDuplicateStops() throws Exception {
        mvc.perform(post("/api/schedules/12345").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(ROUTE)).andExpect(status().isCreated()).andExpect(jsonPath("$[0].stationCode").value("AAA"));
        mvc.perform(post("/api/schedules/12345").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(ROUTE.replace(" aaa ","AAA").replace(" cCc ","CCC")))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/schedules/12345")).andExpect(status().isOk()).andExpect(jsonPath("$[1].stationCode").value("CCC"));
        assertEquals(2,schedules.count()); verify(catalogue).getStation("AAA");
    }
    @Test void normalizationDetectsDuplicateStationBeforeSavingRoute() throws Exception {
        mvc.perform(post("/api/schedules/12345").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(ROUTE.replace(" cCc ","AAA")))
            .andExpect(status().isBadRequest());
        assertEquals(0,schedules.count()); verifyNoInteractions(catalogue);
    }

    @Test void invalidEndpointTimesExplainTheProblemAndNeverPersist() throws Exception {
        mvc.perform(post("/api/schedules/12345").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(ROUTE.replace("\"departureTime\":\"10:00:00\"", "\"arrivalTime\":\"09:00:00\",\"departureTime\":\"10:00:00\"")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Source must begin at distance 0 on day 1 with no arrival time"));
        mvc.perform(post("/api/schedules/12345").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(ROUTE.replace("\"arrivalTime\":\"12:00:00\"", "\"arrivalTime\":\"12:00:00\",\"departureTime\":\"12:01:00\"")))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Destination must have no departure time"));
        assertEquals(0,schedules.count()); verifyNoInteractions(catalogue);
    }
}
