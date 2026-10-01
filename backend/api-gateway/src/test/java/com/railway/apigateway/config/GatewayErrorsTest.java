package com.railway.apigateway.config;
import org.junit.jupiter.api.Test;import org.springframework.mock.http.server.reactive.MockServerHttpRequest;import org.springframework.mock.web.server.MockServerWebExchange;import org.springframework.http.HttpStatus;import static org.junit.jupiter.api.Assertions.*;
class GatewayErrorsTest {
 @Test void routingFailuresHaveSafeConsistentEnvelope(){var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/unavailable"));new GatewayErrors().handle(exchange,new RuntimeException("private-internal-host")).block();assertEquals(HttpStatus.SERVICE_UNAVAILABLE,exchange.getResponse().getStatusCode());String body=exchange.getResponse().getBodyAsString().block();assertTrue(body.contains("requestId"));assertTrue(body.contains("fieldErrors"));assertFalse(body.contains("private-internal-host"));}
}
