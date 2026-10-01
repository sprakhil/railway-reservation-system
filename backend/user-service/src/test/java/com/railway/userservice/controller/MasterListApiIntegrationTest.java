package com.railway.userservice.controller;

import com.jayway.jsonpath.JsonPath;
import com.railway.userservice.repository.SavedPassengerRepository;
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

@SpringBootTest(properties={
    "spring.config.import=", "spring.cloud.config.enabled=false", "spring.cloud.discovery.enabled=false",
    "eureka.client.enabled=false", "spring.cloud.compatibility-verifier.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:master-api;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.open-in-view=false",
    "jwt.secret=test-only-jwt-secret-with-at-least-32-bytes", "railway.session.check-enabled=false"
})
@AutoConfigureMockMvc
class MasterListApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SavedPassengerRepository passengers;
    @BeforeEach void clear(){passengers.deleteAll();}
    private String body(String preference){return "{\"name\":\"Demo\",\"age\":25,\"gender\":\"OTHER\",\"berthPreference\":"+preference+"}";}
    @Test void allowedPreferencesMatchBookingAndInvalidInputsNeverPersist() throws Exception {
        for(String preference:new String[]{"LOWER","MIDDLE","UPPER","SIDE_LOWER","SIDE_UPPER","NONE"})
            mvc.perform(post("/api/users/me/passengers").with(user("alice").roles("PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content(body("\""+preference+"\""))).andExpect(status().isCreated());
        mvc.perform(post("/api/users/me/passengers").with(user("alice").roles("PASSENGER"))
            .contentType(MediaType.APPLICATION_JSON).content(body("null"))).andExpect(status().isCreated());
        for(String preference:new String[]{"WINDOW","lower",""," LOWER "})
            mvc.perform(post("/api/users/me/passengers").with(user("alice").roles("PASSENGER"))
                .contentType(MediaType.APPLICATION_JSON).content(body("\""+preference+"\""))).andExpect(status().isBadRequest());
        assertEquals(7,passengers.count());
    }
    @Test void masterListCrudEnforcesOwnershipAcrossHttpAndDatabase() throws Exception {
        String created=mvc.perform(post("/api/users/me/passengers").with(user("alice").roles("PASSENGER"))
            .contentType(MediaType.APPLICATION_JSON).content(body("\"LOWER\""))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Number id=JsonPath.read(created,"$.id");
        mvc.perform(get("/api/users/me/passengers").with(user("bob").roles("PASSENGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(put("/api/users/me/passengers/"+id).with(user("bob").roles("PASSENGER"))
            .contentType(MediaType.APPLICATION_JSON).content(body("null"))).andExpect(status().isNotFound());
        mvc.perform(put("/api/users/me/passengers/"+id).with(user("alice").roles("PASSENGER"))
            .contentType(MediaType.APPLICATION_JSON).content(body("\"UPPER\""))).andExpect(status().isOk()).andExpect(jsonPath("$.berthPreference").value("UPPER"));
        mvc.perform(delete("/api/users/me/passengers/"+id).with(user("alice").roles("PASSENGER"))).andExpect(status().isNoContent());
        assertEquals(0,passengers.count());
    }
}
