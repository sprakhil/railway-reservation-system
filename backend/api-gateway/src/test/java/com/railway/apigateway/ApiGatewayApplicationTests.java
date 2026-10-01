package com.railway.apigateway;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.compatibility-verifier.enabled=false"
})
class ApiGatewayApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that the WebFlux context and CORS beans load without crashing
    }
}
