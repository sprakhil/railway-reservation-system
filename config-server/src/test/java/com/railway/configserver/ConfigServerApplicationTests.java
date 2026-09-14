package com.railway.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTests {

    @Autowired
    private Environment environment;

    @Test
    void contextLoads() {
        // Assert that the context loads successfully and Environment is injected
        assertNotNull(environment, "The environment should not be null");
    }

    @Test
    void verifyNativeProfileIsActive() {
        // Assert that the native profile is active
        String[] activeProfiles = environment.getActiveProfiles();
        assertEquals("native", activeProfiles[0], "Native profile should be active");
    }
}