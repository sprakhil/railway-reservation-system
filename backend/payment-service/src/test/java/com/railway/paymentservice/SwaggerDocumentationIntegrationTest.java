package com.railway.paymentservice;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Fetches the real generated document through the service's HTTP and security filters. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.compatibility-verifier.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:payment-docs;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "railway.payment.reconciliation-enabled=false",
        "jwt.secret=offline-test-secret-at-least-thirty-two-characters-long",
        "springdoc.api-docs.path=/api/payments/v3/api-docs"
})
class SwaggerDocumentationIntegrationTest {
    @LocalServerPort int port;

    @Test void exposesProjectDocumentationWithoutCredentials() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:"+port+"/api/payments/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        var doc = JsonPath.parse(response.body());
        assertEquals("Railway Reservation - Razorpay test payments", doc.read("$.info.title"));
        assertEquals("/", doc.read("$.servers[0].url"));
        assertEquals("bearer", doc.read("$.components.securitySchemes.bearerAuth.scheme"));
        assertNotNull(doc.read("$.security[0].bearerAuth"));
        Map<String,Object> paths = doc.read("$.paths");
        assertTrue(paths.keySet().stream().allMatch(p->p.startsWith("/api/")));
        assertFalse(paths.containsKey("/api/auth/google"));
        assertEquals("Create a test order for my booking", doc.read("$.paths['/api/payments/create-order'].post.summary"));
        assertNotNull(doc.read("$.paths['/api/payments/create-order'].post.requestBody.content['application/json'].example"));
        assertEquals("Verify the signed test checkout result", doc.read("$.paths['/api/payments/verify'].post.summary"));
        assertNotNull(doc.read("$.paths['/api/payments/verify'].post.requestBody.content['application/json'].example"));
        assertEquals("Read payment and refund progress", doc.read("$.paths['/api/payments/{pnr}/status'].get.summary"));
        assertEquals("Receive a signed Razorpay test webhook", doc.read("$.paths['/api/payments/webhook'].post.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/payments/webhook'].post.security")).isEmpty());


    }
}
