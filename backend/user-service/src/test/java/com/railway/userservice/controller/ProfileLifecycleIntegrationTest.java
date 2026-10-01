package com.railway.userservice.controller;

import com.railway.userservice.repository.UserProfileRepository;
import com.railway.userservice.service.AuthAccountClient;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
    "spring.cloud.compatibility-verifier.enabled=false",
    "spring.datasource.url=jdbc:h2:mem:profile-lifecycle;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop", "jwt.secret=test-only-jwt-secret-with-at-least-32-bytes",
    "railway.session.check-enabled=false"
})
@AutoConfigureMockMvc
class ProfileLifecycleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserProfileRepository profiles;
    @MockitoBean AuthAccountClient accounts;
    @BeforeEach void setup() { profiles.deleteAll(); }

    @Test void registeredAccountCanReadBeforeSavingAndSavedDetailsSurviveLaterReads() throws Exception {
        when(accounts.exists("alice")).thenReturn(true);
        for (String path : new String[]{"/api/users/me", "/api/users/alice"}) {
            mvc.perform(get(path).with(user("alice").roles("PASSENGER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
        }
        assertEquals(0, profiles.count());
        mvc.perform(put("/api/users/me").with(user("alice").roles("PASSENGER"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"bob\",\"firstName\":\"Alice\",\"address\":\"Mumbai\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"));
        reset(accounts);
        mvc.perform(get("/api/users/me").with(user("alice").roles("PASSENGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.firstName").value("Alice"));
        assertEquals(1, profiles.count());
        verifyNoInteractions(accounts);
    }

    @Test void accessRulesAndMissingAccountsArePreserved() throws Exception {
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/users/bob").with(user("alice").roles("PASSENGER"))).andExpect(status().isForbidden());
        verify(accounts).canonicalUsername("bob");
        reset(accounts);
        when(accounts.exists("bob")).thenReturn(true);
        when(accounts.canonicalUsername("bob")).thenReturn(java.util.Optional.of("bob"));
        mvc.perform(get("/api/users/bob").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("bob"));
        mvc.perform(get("/api/users/nobody").with(user("admin").roles("ADMIN"))).andExpect(status().isNotFound());
        when(accounts.canonicalUsername("offline")).thenThrow(new RestClientException("private detail"));
        mvc.perform(get("/api/users/offline").with(user("admin").roles("ADMIN")))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
        assertEquals(0, profiles.count());
    }
    @Test void verifiedEmailIsReturnedForBothRolesAndCannotBeChangedInProfileBody() throws Exception {
        for(String role:new String[]{"PASSENGER","ADMIN"}){
            String owner=role.toLowerCase();when(accounts.exists(owner)).thenReturn(true);
            mvc.perform(get("/api/users/me").with(user(owner).roles(role)).requestAttr("loggedInUserEmail",owner+"@example.invalid"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(owner+"@example.invalid"));
            mvc.perform(put("/api/users/me").with(user(owner).roles(role)).requestAttr("loggedInUserEmail",owner+"@example.invalid")
                .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Traveller\",\"address\":\"Mumbai\",\"email\":\"attacker@example.invalid\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(owner+"@example.invalid"));
        }
    }
}
