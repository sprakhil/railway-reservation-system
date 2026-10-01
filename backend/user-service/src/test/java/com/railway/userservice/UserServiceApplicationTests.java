package com.railway.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.compatibility-verifier.enabled=false", "spring.datasource.url=jdbc:h2:mem:user-context;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-only-jwt-secret-with-at-least-32-bytes"
})
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
