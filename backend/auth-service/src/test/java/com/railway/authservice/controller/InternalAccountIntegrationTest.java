package com.railway.authservice.controller;

import com.railway.authservice.repository.AuthUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={
    "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
    "spring.cloud.compatibility-verifier.enabled=false", "spring.datasource.url=jdbc:h2:mem:internal-account;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.mail.host=localhost", "jwt.secret=test-only-jwt-secret-with-at-least-32-bytes", "jwt.expirationMs=900000",
    "auth.bootstrap-admin.enabled=false", "railway.internal.token=test-internal-token-at-least-32-characters"
})
@AutoConfigureMockMvc
class InternalAccountIntegrationTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthUserRepository users;

    @Test void accountLookupRequiresServiceCredentialsAndExposesOnlyExistence() throws Exception {
        mvc.perform(get("/internal/auth/accounts/alice/exists")).andExpect(status().isForbidden());
        mvc.perform(get("/internal/auth/accounts/alice/exists").header("X-Service-Token", "wrong"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(users);
        when(users.existsByUsername("alice")).thenReturn(true);
        mvc.perform(get("/internal/auth/accounts/alice/exists").header("X-Service-Token", "test-internal-token-at-least-32-characters"))
            .andExpect(status().isOk()).andExpect(content().json("{\"exists\":true}"));
        mvc.perform(get("/internal/auth/accounts/nobody/exists").header("X-Service-Token", "test-internal-token-at-least-32-characters"))
            .andExpect(status().isOk()).andExpect(content().json("{\"exists\":false}"));
    }
}
