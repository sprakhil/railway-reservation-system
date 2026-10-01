package com.railway.apigateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {
    private CorsConfig config(String origins) {
        var config = new CorsConfig(); ReflectionTestUtils.setField(config, "frontendOrigins", origins); return config;
    }

    @Test void configuredFrontendCanSendCredentialsAndIdempotencyKey() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.options("http://localhost:8080/api/payments/orders")
                .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,idempotency-key"));
        config("http://localhost:4200, https://rail.example.test").corsWebFilter().filter(exchange, ignored -> Mono.empty()).block();
        assertEquals("http://localhost:4200", exchange.getResponse().getHeaders().getAccessControlAllowOrigin());
        assertTrue(exchange.getResponse().getHeaders().getAccessControlAllowCredentials());
        assertTrue(exchange.getResponse().getHeaders().getAccessControlAllowHeaders().contains("idempotency-key"));
    }

    @Test void unrelatedOriginIsRejected() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.options("http://localhost:8080/api/auth/google/exchange")
                .header(HttpHeaders.ORIGIN, "https://untrusted.example.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"));
        config("http://localhost:4200").corsWebFilter().filter(exchange, ignored -> Mono.empty()).block();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertNull(exchange.getResponse().getHeaders().getAccessControlAllowOrigin());
    }

    @Test void wildcardOriginsCannotBeCombinedWithCredentials() {
        assertThrows(IllegalStateException.class, () -> config("*").corsWebFilter());
    }
}
