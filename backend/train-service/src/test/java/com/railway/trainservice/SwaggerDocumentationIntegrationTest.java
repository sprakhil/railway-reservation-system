package com.railway.trainservice;

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

        "springdoc.api-docs.path=/api/trains/v3/api-docs"
})
class SwaggerDocumentationIntegrationTest {
    @LocalServerPort int port;

    @Test void exposesProjectDocumentationWithoutCredentials() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:"+port+"/api/trains/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        var doc = JsonPath.parse(response.body());
        assertEquals("Railway Reservation - Station and train catalogue", doc.read("$.info.title"));
        assertEquals("/", doc.read("$.servers[0].url"));
        assertEquals("bearer", doc.read("$.components.securitySchemes.bearerAuth.scheme"));
        assertNotNull(doc.read("$.security[0].bearerAuth"));
        Map<String,Object> paths = doc.read("$.paths");
        assertTrue(paths.keySet().stream().allMatch(p->p.startsWith("/api/")));
        assertFalse(paths.containsKey("/api/auth/google"));
        assertEquals("List stations", doc.read("$.paths['/api/stations'].get.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/stations'].get.security")).isEmpty());
        assertEquals("Read a station", doc.read("$.paths['/api/stations/{stationId}'].get.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/stations/{stationId}'].get.security")).isEmpty());
        assertEquals("Create a station", doc.read("$.paths['/api/stations'].post.summary"));
        assertNotNull(doc.read("$.paths['/api/stations'].post.requestBody.content['application/json'].example"));
        assertEquals("Update a station", doc.read("$.paths['/api/stations/{stationId}'].put.summary"));
        assertNotNull(doc.read("$.paths['/api/stations/{stationId}'].put.requestBody.content['application/json'].example"));
        assertEquals("List trains", doc.read("$.paths['/api/trains'].get.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/trains'].get.security")).isEmpty());
        assertEquals("Read a train", doc.read("$.paths['/api/trains/{trainNumber}'].get.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/trains/{trainNumber}'].get.security")).isEmpty());
        assertEquals("Create a train", doc.read("$.paths['/api/trains'].post.summary"));
        assertNotNull(doc.read("$.paths['/api/trains'].post.requestBody.content['application/json'].example"));
        assertEquals("Update a train", doc.read("$.paths['/api/trains/{trainNumber}'].put.summary"));
        assertNotNull(doc.read("$.paths['/api/trains/{trainNumber}'].put.requestBody.content['application/json'].example"));
        assertEquals("Find a station by code", doc.read("$.paths['/api/stations/code/{stationCode}'].get.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/stations/code/{stationCode}'].get.security")).isEmpty());
        assertEquals(true, doc.read("$.components.schemas.StationDto.properties.stationId.readOnly"));
        assertEquals(true, doc.read("$.components.schemas.TrainDto.properties.trainId.readOnly"));

    }
}
