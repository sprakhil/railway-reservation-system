package com.railway.reservationservice.service;
import com.railway.reservationservice.client.*;
import com.railway.reservationservice.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.railway.reservationservice.service.JourneyPlannerTest.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:planner-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class JourneyPlannerApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean TrainCatalogClient trains;
    @MockitoBean StationCatalogClient stations;
    @MockitoBean ScheduleCatalogClient schedules;
    @Autowired Clock clock;
    String date;
    @BeforeEach void setup(){
        date=LocalDate.now(clock.withZone(ZoneId.of("Asia/Kolkata"))).plusDays(1).toString();
        when(stations.station("AAA")).thenReturn(station("AAA",19,73));when(stations.station("CCC")).thenReturn(station("CCC",15,74));
        when(trains.getPage(0,100)).thenReturn(new PageResult<>(List.of(train("T1"),train("T2")),0,100,2,1));
        when(schedules.getSchedule("T1")).thenReturn(List.of(stop("AAA",1,1,"10:00",0),stop("XXX",2,1,"12:00",100)));
        when(schedules.getSchedule("T2")).thenReturn(List.of(stop("XXX",1,1,"13:00",0),stop("CCC",2,1,"15:00",100)));
    }
    @Test void anonymousPlannerUsesRealFareAndInventoryServices()throws Exception{
        mvc.perform(get("/api/reservations/alternatives").param("fromStation"," aaa ").param("toStation","CCC").param("dateOfJourney",date).param("seatClass","SL").param("passengers","2").param("nearby","false"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.options[0].legs.length()").value(2))
            .andExpect(jsonPath("$.options[0].totalFare").value(440)).andExpect(jsonPath("$.options[0].allocation").value("CNF"))
            .andExpect(jsonPath("$.options[0].legs[1].availableSeats").value(10)).andExpect(jsonPath("$.options[0].transferMinutes").value(60));
    }
    @Test void badParametersReturnConsistentErrorsAndSwaggerMarksPublicSearch()throws Exception{
        mvc.perform(get("/api/reservations/alternatives").param("fromStation","AAA").param("toStation","CCC").param("dateOfJourney",date).param("seatClass","SL").param("radiusKm","900"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").exists()).andExpect(jsonPath("$.requestId").exists());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/reservations/alternatives'].get.security").isEmpty());
    }
    @Test void discoveryIsPublicGroupsClassesAndNeedsNoPassengerCount()throws Exception {
        var t=train("T1");var ac=new CatalogTrain.ClassConfiguration();
        ac.setSeatClass("CLASS_3A");ac.setCoachCount(1);ac.setSeatsPerCoach(72);ac.setBaseFare(new java.math.BigDecimal("100"));ac.setRatePerKm(new java.math.BigDecimal("1.50"));ac.setMinimumFare(new java.math.BigDecimal("300"));
        t.setSeatClasses(Set.of("SL","CLASS_3A"));t.setClassConfigurations(List.of(t.getClassConfigurations().getFirst(),ac));
        when(trains.getTrains()).thenReturn(List.of(t));
        mvc.perform(get("/api/reservations/trains").param("fromStation","AAA").param("toStation","XXX").param("dateOfJourney",date))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].classes.length()").value(2)).andExpect(jsonPath("$[0].classes[0].passengerCount").value(1))
            .andExpect(jsonPath("$[0].classes[0].availableSeats").value(10)).andExpect(jsonPath("$[0].classes[1].availableSeats").value(72));
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(jsonPath("$.paths['/api/reservations/trains'].get.security").isEmpty());
    }
}
