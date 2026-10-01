package com.railway.scheduleservice;

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

        "springdoc.api-docs.path=/api/schedules/v3/api-docs"
})
class SwaggerDocumentationIntegrationTest {
    @LocalServerPort int port;

    @Test void exposesProjectDocumentationWithoutCredentials() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:"+port+"/api/schedules/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        var doc = JsonPath.parse(response.body());
        assertEquals("Railway Reservation - Train timetables", doc.read("$.info.title"));
        assertEquals("/", doc.read("$.servers[0].url"));
        assertEquals("bearer", doc.read("$.components.securitySchemes.bearerAuth.scheme"));
        assertNotNull(doc.read("$.security[0].bearerAuth"));
        Map<String,Object> paths = doc.read("$.paths");
        assertTrue(paths.keySet().stream().allMatch(p->p.startsWith("/api/")));
        assertFalse(paths.containsKey("/api/auth/google"));
        assertEquals("Read ordered timetable", doc.read("$.paths['/api/schedules/{trainNumber}'].get.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/schedules/{trainNumber}'].get.security")).isEmpty());
        assertEquals("Publish a complete timetable", doc.read("$.paths['/api/schedules/{trainNumber}'].post.summary"));
        assertNotNull(doc.read("$.paths['/api/schedules/{trainNumber}'].post.requestBody.content['application/json'].example"));
        assertEquals(true, doc.read("$.components.schemas.ScheduleDto.properties.id.readOnly"));

    }
}
