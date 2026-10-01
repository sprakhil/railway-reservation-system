package com.railway.userservice;

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
        "spring.cloud.compatibility-verifier.enabled=false", "spring.datasource.url=jdbc:h2:mem:user-context;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "jwt.secret=test-only-jwt-secret-with-at-least-32-bytes",
        "springdoc.api-docs.path=/api/users/v3/api-docs"
})
class SwaggerDocumentationIntegrationTest {
    @LocalServerPort int port;

    @Test void exposesProjectDocumentationWithoutCredentials() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:"+port+"/api/users/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        var doc = JsonPath.parse(response.body());
        assertEquals("Railway Reservation - Profiles and passenger Master List", doc.read("$.info.title"));
        assertEquals("/", doc.read("$.servers[0].url"));
        assertEquals("bearer", doc.read("$.components.securitySchemes.bearerAuth.scheme"));
        assertNotNull(doc.read("$.security[0].bearerAuth"));
        Map<String,Object> paths = doc.read("$.paths");
        assertTrue(paths.keySet().stream().allMatch(p->p.startsWith("/api/")));
        assertFalse(paths.containsKey("/api/auth/google"));
        assertEquals("Read my profile", doc.read("$.paths['/api/users/me'].get.summary"));
        assertEquals("Create or update my profile", doc.read("$.paths['/api/users/me'].put.summary"));
        assertNotNull(doc.read("$.paths['/api/users/me'].put.requestBody.content['application/json'].example"));
        assertEquals("Read a profile by username", doc.read("$.paths['/api/users/{username}'].get.summary"));
        assertEquals("List my saved passengers", doc.read("$.paths['/api/users/me/passengers'].get.summary"));
        assertEquals("Save a passenger", doc.read("$.paths['/api/users/me/passengers'].post.summary"));
        assertNotNull(doc.read("$.paths['/api/users/me/passengers'].post.requestBody.content['application/json'].example"));
        assertEquals("Update a saved passenger", doc.read("$.paths['/api/users/me/passengers/{id}'].put.summary"));
        assertNotNull(doc.read("$.paths['/api/users/me/passengers/{id}'].put.requestBody.content['application/json'].example"));
        assertEquals("Delete a saved passenger", doc.read("$.paths['/api/users/me/passengers/{id}'].delete.summary"));
        assertEquals(true, doc.read("$.components.schemas.UserProfileDto.properties.username.readOnly"));

    }
}
