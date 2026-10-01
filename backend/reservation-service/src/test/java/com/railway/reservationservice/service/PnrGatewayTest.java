package com.railway.reservationservice.service;

import com.railway.reservationservice.client.PnrServiceClient;
import com.railway.reservationservice.exception.BookingException;
import io.github.resilience4j.circuitbreaker.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PnrGatewayTest {
    private final PnrServiceClient client = mock(PnrServiceClient.class);
    private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowSize(2).minimumNumberOfCalls(2).failureRateThreshold(50)
            .permittedNumberOfCallsInHalfOpenState(1).build());
    private final PnrGateway gateway = new PnrGateway(client, registry);

    @Test void returnsRealPnrWithoutRetrying() {
        doReturn("1234567890").when(client).generatePnr();
        assertEquals("1234567890", gateway.generatePnr());
        verify(client, times(1)).generatePnr();
    }

    @Test void opensOnFailuresSkipsDownstreamCallsAndRecoversAfterProbe() {
        when(client.generatePnr()).thenThrow(new IllegalStateException("down"));
        for (int i = 0; i < 3; i++) {
            var ex = assertThrows(BookingException.class, gateway::generatePnr);
            assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        }
        verify(client, times(2)).generatePnr();
        var breaker = registry.circuitBreaker("pnrService");
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        breaker.transitionToHalfOpenState();
        doReturn("1234567890").when(client).generatePnr();
        assertEquals("1234567890", gateway.generatePnr());
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }

    @Test void rejectsMalformedPnrInsteadOfCreatingFakeSuccess() {
        when(client.generatePnr()).thenReturn(null, "bad");
        assertThrows(BookingException.class, gateway::generatePnr);
        assertThrows(BookingException.class, gateway::generatePnr);
        assertEquals(CircuitBreaker.State.OPEN, registry.circuitBreaker("pnrService").getState());
    }
}

