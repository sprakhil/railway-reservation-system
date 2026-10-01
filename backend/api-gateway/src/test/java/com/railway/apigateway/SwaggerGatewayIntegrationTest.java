package com.railway.apigateway;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Uses the production route/UI properties, with HTTP stubs replacing discovery targets. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.config.import=", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
        "spring.cloud.compatibility-verifier.enabled=false", "server.port=0"
})
@TestPropertySource(locations = "file:../config-server/src/main/resources/config-repo/api-gateway.properties")
class SwaggerGatewayIntegrationTest {
    private static final HttpServer SERVICES = startServices();
    @LocalServerPort int port;

    private static HttpServer startServices() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                String body = "{\"path\":\""+exchange.getRequestURI().getPath()+"\",\"authorization\":\""+(auth==null?"":auth)+"\"}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException ex) { throw new ExceptionInInitializerError(ex); }
    }

    @DynamicPropertySource static void routeTargets(DynamicPropertyRegistry properties) {
        // Spring replaces a whole indexed list when its source has higher precedence.
        // Preserve the real IDs and predicates while replacing only remote destinations.
        Properties production = new Properties();
        try (var input = java.nio.file.Files.newInputStream(java.nio.file.Path.of(
                "../config-server/src/main/resources/config-repo/api-gateway.properties"))) {
            production.load(input);
        } catch (java.io.IOException ex) { throw new IllegalStateException(ex); }
        for (String key : production.stringPropertyNames()) {
            if (key.startsWith("spring.cloud.gateway.server.webflux.routes["))
                properties.add(key, () -> key.endsWith(".uri")
                        ? "http://127.0.0.1:"+SERVICES.getAddress().getPort() : production.getProperty(key));
        }
    }
    @AfterAll static void closeServices() { SERVICES.stop(0); }

    private String get(String path, boolean authorize) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:"+port+path));
        if (authorize) request.header("Authorization", "Bearer test-swagger-session");
        var response = HttpClient.newHttpClient().send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }

    @Test void servesAllServiceDefinitionsWithRestartAwareAuthorization() throws Exception {
        var config = JsonPath.parse(get("/v3/api-docs/swagger-config", false));
        assertEquals(true, config.read("$.persistAuthorization"));
        assertEquals("Auth Service", config.read("$['urls.primaryName']"));
        assertEquals("none", config.read("$.validatorUrl"));
        List<Map<String,String>> urls = config.read("$.urls");
        assertEquals(8, urls.size());
        for (var definition : urls) {
            String url = definition.get("url");
            assertEquals(url, JsonPath.parse(get(url, false)).read("$.path"));
        }
        assertTrue(get("/swagger-ui/index.html", false).contains("swagger-ui"));
        String initializer = get("/swagger-ui/swagger-initializer.js", false);
        assertTrue(initializer.contains("railway.swagger.instance"));
        assertTrue(initializer.contains("authActions.logout"));
    }

    @Test void doesNotInventAuthorizationForAnonymousRequests() throws Exception {
        for (String path : List.of("/api/users/me", "/api/reservations/my-bookings", "/api/payments/1234567890/status")) {
            assertEquals("", JsonPath.parse(get(path, false)).read("$.authorization"));
        }
    }
    @Test void forwardsTheSameBearerHeaderAcrossServiceRoutes() throws Exception {
        for (String path : List.of("/api/users/me", "/api/trains", "/api/stations", "/api/schedules/DM001",
                "/api/reservations/my-bookings", "/api/pnr/generate", "/api/payments/1234567890/status")) {
            assertEquals("Bearer test-swagger-session", JsonPath.parse(get(path, true)).read("$.authorization"));
        }
    }
}
