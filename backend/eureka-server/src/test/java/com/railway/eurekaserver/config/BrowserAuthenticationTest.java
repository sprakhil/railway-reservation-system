package com.railway.eurekaserver.config;

import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class BrowserAuthenticationTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(TestConfig.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    @AfterEach void close() { if (context != null) context.close(); }

    @Test void missingCredentialsChallengeBrowserAndKeepJsonError() throws Exception {
        var response = mvc.perform(get("/")).andReturn().getResponse();
        assertEquals(401, response.getStatus());
        assertEquals("Basic realm=\"Railway eureka-server\"", response.getHeader("WWW-Authenticate"));
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
        assertNotNull(response.getHeader("X-Request-ID"));
    }
    @Test void wrongCredentialsAlsoChallengeAndKeepJsonError() throws Exception {
        var response = mvc.perform(get("/").header("Authorization", basic("wrong"))).andReturn().getResponse();
        assertEquals(401, response.getStatus());
        assertNotNull(response.getHeader("WWW-Authenticate"));
        assertTrue(response.getContentAsString().contains("Authentication required"));
    }
    @Test void validCredentialsAccessProtectedEndpoint() throws Exception {
        assertEquals(200, mvc.perform(get("/").header("Authorization", basic("test-password")))
                .andReturn().getResponse().getStatus());
    }
    @Test void healthRemainsAccessibleWithoutCredentials() throws Exception {
        assertEquals(200, mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus());
    }
    private String basic(String password) {
        return "Basic " + Base64.getEncoder().encodeToString(("test-user:" + password).getBytes(StandardCharsets.UTF_8));
    }
    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class TestConfig {
        @Bean InMemoryUserDetailsManager users() {
            return new InMemoryUserDetailsManager(User.withUsername("test-user")
                    .password("{noop}test-password").roles("USER").build());
        }
        @Bean TestEndpoint endpoint() { return new TestEndpoint(); }
    }
    @RestController
    static class TestEndpoint {
        @GetMapping({"/", "/actuator/health"}) String ok() { return "ok"; }
    }
}