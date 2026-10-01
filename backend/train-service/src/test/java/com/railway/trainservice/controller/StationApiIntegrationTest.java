package com.railway.trainservice.controller;

import com.jayway.jsonpath.JsonPath;
import com.railway.trainservice.repository.StationRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="spring.datasource.url=jdbc:h2:mem:station-api;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class StationApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired StationRepository stations;
    @BeforeEach void clear(){stations.deleteAll();}
    private String station(String code){return "{\"stationCode\":\""+code+"\",\"stationName\":\"Demo Station\"}";}

    @Test void createLookupUpdateAndDuplicateChecksUseCanonicalStationCodes() throws Exception {
        String created=mvc.perform(post("/api/stations").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(station(" aaa ")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.stationCode").value("AAA")).andReturn().getResponse().getContentAsString();
        Number id=JsonPath.read(created,"$.stationId");
        mvc.perform(get("/api/stations/code/{code}"," aAa ")).andExpect(status().isOk()).andExpect(jsonPath("$.stationCode").value("AAA"));
        mvc.perform(put("/api/stations/"+id).with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(station(" aAa ").replace("Demo Station","Renamed")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.stationName").value("Renamed"));
        mvc.perform(post("/api/stations").with(user("admin").roles("ADMIN"))
            .contentType(MediaType.APPLICATION_JSON).content(station("aaa"))).andExpect(status().isConflict());
        assertEquals(1,stations.count()); assertTrue(stations.findByStationCode("AAA").isPresent());
    }
    @Test void normalizationDoesNotPermitBlankOrMalformedCodes() throws Exception {
        for(String code:new String[]{"   "," A A ","AAA!"})
            mvc.perform(post("/api/stations").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(station(code))).andExpect(status().isBadRequest());
        assertEquals(0,stations.count());
    }
    @Test void passengerCannotCreateCatalogueData() throws Exception {
        mvc.perform(post("/api/stations").with(user("alice").roles("PASSENGER"))
            .contentType(MediaType.APPLICATION_JSON).content(station("AAA"))).andExpect(status().isForbidden());
        assertEquals(0,stations.count());
    }
    @Test void coordinatesAreOptionalPairedBoundedAndPersisted() throws Exception {
        for(String coordinates:new String[]{",\"latitude\":19",",\"latitude\":91,\"longitude\":73",",\"latitude\":19,\"longitude\":181"})
            mvc.perform(post("/api/stations").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
                .content(station("AAA").replace("}",coordinates+"}"))).andExpect(status().isBadRequest());
        String created=mvc.perform(post("/api/stations").with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON)
            .content(station("AAA").replace("}",",\"latitude\":19.1,\"longitude\":73.2}")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.latitude").value(19.1)).andReturn().getResponse().getContentAsString();
        Number id=JsonPath.read(created,"$.stationId");assertEquals(73.2,stations.findByStationCode("AAA").orElseThrow().getLongitude());
        mvc.perform(get("/api/stations/code/AAA")).andExpect(jsonPath("$.longitude").value(73.2));
        mvc.perform(put("/api/stations/"+id).with(user("admin").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(station("AAA"))).andExpect(status().isOk());
        assertNull(stations.findByStationCode("AAA").orElseThrow().getLatitude());
    }
}
