package com.railway.authservice;

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
        "spring.cloud.compatibility-verifier.enabled=false", "spring.datasource.url=jdbc:h2:mem:auth-context;MODE=MySQL",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.mail.host=localhost", "jwt.secret=test-only-jwt-secret-with-at-least-32-bytes", "jwt.expirationMs=900000",
        "auth.bootstrap-admin.enabled=false",
        "springdoc.api-docs.path=/api/auth/v3/api-docs"
})
class SwaggerDocumentationIntegrationTest {
    @LocalServerPort int port;

    @Test void exposesProjectDocumentationWithoutCredentials() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://localhost:"+port+"/api/auth/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), response.body());
        var doc = JsonPath.parse(response.body());
        assertEquals("Railway Reservation - Authentication and administration", doc.read("$.info.title"));
        assertEquals("/", doc.read("$.servers[0].url"));
        assertEquals("bearer", doc.read("$.components.securitySchemes.bearerAuth.scheme"));
        assertNotNull(doc.read("$.security[0].bearerAuth"));
        Map<String,Object> paths = doc.read("$.paths");
        assertTrue(paths.keySet().stream().allMatch(p->p.startsWith("/api/")));
        assertFalse(paths.containsKey("/api/auth/google"));
        assertEquals("Register a passenger", doc.read("$.paths['/api/auth/register'].post.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/auth/register'].post.security")).isEmpty());
        assertNotNull(doc.read("$.paths['/api/auth/register'].post.requestBody.content['application/json'].example"));
        assertEquals("Verify registration OTP", doc.read("$.paths['/api/auth/verify-otp'].post.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/auth/verify-otp'].post.security")).isEmpty());
        assertNotNull(doc.read("$.paths['/api/auth/verify-otp'].post.requestBody.content['application/json'].example"));
        assertEquals("Resend registration OTP", doc.read("$.paths['/api/auth/resend-otp'].post.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/auth/resend-otp'].post.security")).isEmpty());
        assertEquals("Log in with username or email", doc.read("$.paths['/api/auth/login'].post.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/auth/login'].post.security")).isEmpty());
        assertNotNull(doc.read("$.paths['/api/auth/login'].post.requestBody.content['application/json'].example"));
        assertEquals("Refresh access token", doc.read("$.paths['/api/auth/refresh'].post.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/auth/refresh'].post.security")).isEmpty());
        assertNotNull(doc.read("$.paths['/api/auth/refresh'].post.requestBody.content['application/json'].example"));
        assertEquals("Request a password-reset OTP", doc.read("$.paths['/api/auth/forgot-password'].post.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/auth/forgot-password'].post.security")).isEmpty());
        assertEquals("Reset password with emailed OTP", doc.read("$.paths['/api/auth/reset-password'].post.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/auth/reset-password'].post.security")).isEmpty());
        assertNotNull(doc.read("$.paths['/api/auth/reset-password'].post.requestBody.content['application/json'].example"));
        assertEquals("Log out this session", doc.read("$.paths['/api/auth/logout'].post.summary"));
        assertEquals("Log out every session", doc.read("$.paths['/api/auth/logout-all'].post.summary"));
        assertEquals("Start Google sign-in in a browser", doc.read("$.paths['/api/auth/google/start'].get.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/auth/google/start'].get.security")).isEmpty());
        assertEquals("Receive Google OAuth callback", doc.read("$.paths['/api/auth/google/callback'].get.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/auth/google/callback'].get.security")).isEmpty());
        assertEquals("Exchange the one-time browser code", doc.read("$.paths['/api/auth/google/exchange'].post.summary"));
        assertTrue(((List<?>)doc.read("$.paths['/api/auth/google/exchange'].post.security")).isEmpty());
        assertNotNull(doc.read("$.paths['/api/auth/google/exchange'].post.requestBody.content['application/json'].example"));
        assertEquals("Invite a verified account to become admin", doc.read("$.paths['/api/auth/admin/invitations'].post.summary"));
        assertNotNull(doc.read("$.paths['/api/auth/admin/invitations'].post.requestBody.content['application/json'].example"));
        assertEquals("List administrator invitations", doc.read("$.paths['/api/auth/admin/invitations'].get.summary"));
        assertEquals("Revoke an unused invitation", doc.read("$.paths['/api/auth/admin/invitations/{id}'].delete.summary"));
        assertEquals("Accept an emailed admin invitation", doc.read("$.paths['/api/auth/admin-invitations/accept'].post.summary"));
        assertNotNull(doc.read("$.paths['/api/auth/admin-invitations/accept'].post.requestBody.content['application/json'].example"));


    }
}
