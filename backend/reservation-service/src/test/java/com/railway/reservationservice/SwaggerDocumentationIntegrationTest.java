package com.railway.reservationservice;

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

        "springdoc.api-docs.path=/api/reservations/v3/api-docs"
})
class SwaggerDocumentationIntegrationTest {
    @LocalServerPort int port;

    @Test void exposesProjectDocumentationWithoutCredentials() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:"+port+"/api/reservations/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        var doc = JsonPath.parse(response.body());
        assertEquals("Railway Reservation - Search, booking and tickets", doc.read("$.info.title"));
        assertEquals("/", doc.read("$.servers[0].url"));
        assertEquals("bearer", doc.read("$.components.securitySchemes.bearerAuth.scheme"));
        assertNotNull(doc.read("$.security[0].bearerAuth"));
        Map<String,Object> paths = doc.read("$.paths");
        assertTrue(paths.keySet().stream().allMatch(p->p.startsWith("/api/")));
        assertFalse(paths.containsKey("/api/auth/google"));
        assertEquals("Search matching journeys", doc.read("$.paths['/api/reservations/search'].get.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/reservations/search'].get.security")).isEmpty());
        assertEquals("Quote one train and journey", doc.read("$.paths['/api/reservations/quote'].get.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/reservations/quote'].get.security")).isEmpty());
        assertEquals("Create a two-minute checkout hold", doc.read("$.paths['/api/reservations/book'].post.summary"));
        assertNotNull(doc.read("$.paths['/api/reservations/book'].post.requestBody.content['application/json'].example"));
        assertEquals("List and filter my booking history", doc.read("$.paths['/api/reservations/my-bookings'].get.summary"));
        assertEquals("Read a booking by PNR", doc.read("$.paths['/api/reservations/pnr/{pnr}'].get.summary"));
        assertEquals("Preview cancellation and test refund", doc.read("$.paths['/api/reservations/{pnr}/cancellation-preview'].get.summary"));
        assertEquals("Cancel the entire booking", doc.read("$.paths['/api/reservations/{pnr}'].delete.summary"));
        assertEquals("Download a PDF ticket", doc.read("$.paths['/api/reservations/{pnr}/ticket.pdf'].get.summary"));
        assertEquals(true, doc.read("$.components.schemas.PassengerDto.properties.seatNumber.readOnly"));
        assertEquals(true, doc.read("$.components.schemas.PassengerDto.properties.bookingStatus.readOnly"));
        assertEquals(2, ((List<?>)doc.read("$.paths['/api/reservations/book'].post.requestBody.content['application/json'].example.passengers")).size());

    }
}
