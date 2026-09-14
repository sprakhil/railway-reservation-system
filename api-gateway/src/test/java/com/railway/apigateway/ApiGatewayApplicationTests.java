package com.railway.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the WebFlux context and CORS beans load without crashing
    }
}