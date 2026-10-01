package com.railway.userservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AuthAccountClientTest {
    @Test void sendsServiceCredentialsAndDistinguishesUnknownFromUnavailable() {
        var builder = RestClient.builder().baseUrl("http://auth");
        var server = MockRestServiceServer.bindTo(builder).build();
        String token = "test-internal-token-at-least-32-characters";
        var client = new AuthAccountClient(builder.build(), token);
        server.expect(requestTo("http://auth/internal/auth/accounts/alice/exists"))
            .andExpect(header("X-Service-Token", token)).andRespond(withSuccess("{\"exists\":true}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://auth/internal/auth/accounts/nobody/exists"))
            .andRespond(withSuccess("{\"exists\":false}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://auth/internal/auth/accounts/unavailable/exists")).andRespond(withServerError());
        assertTrue(client.exists("alice"));
        assertFalse(client.exists("nobody"));
        assertThrows(RestClientException.class, () -> client.exists("unavailable"));
        server.verify();
    }

    @Test void malformedResponseCannotBecomeAnUnknownAccount() {
        var builder = RestClient.builder().baseUrl("http://auth");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://auth/internal/auth/accounts/alice/exists"))
            .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        var client = new AuthAccountClient(builder.build(), "test-internal-token-at-least-32-characters");
        assertThrows(RestClientException.class, () -> client.exists("alice"));
        server.verify();
    }
}
