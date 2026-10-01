package com.railway.reservationservice.service;

import com.railway.reservationservice.client.PnrServiceClient;
import com.railway.reservationservice.exception.BookingException;
import feign.FeignException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** A failed PNR allocation must never turn into a fabricated booking or an automatic retry. */
@Service
public class PnrGateway {
    private static final Logger log = LoggerFactory.getLogger(PnrGateway.class);
    private final PnrServiceClient client;
    private final CircuitBreaker breaker;

    public PnrGateway(PnrServiceClient client, CircuitBreakerRegistry registry) {
        this.client = client;
        this.breaker = registry.circuitBreaker("pnrService");
        breaker.getEventPublisher().onStateTransition(event ->
                log.warn("Dependency circuit changed: name=pnrService transition={}", event.getStateTransition()));
    }

    public String generatePnr() {
        try {
            return breaker.executeSupplier(() -> {
                String pnr = client.generatePnr();
                if (pnr == null || !pnr.matches("[0-9]{10}"))
                    throw new IllegalStateException("Invalid PNR service response");
                return pnr;
            });
        } catch (FeignException | CallNotPermittedException | IllegalStateException ex) {
            // Do not log response bodies, PNRs, customer details or internal authorization headers.
            log.warn("PNR allocation unavailable: failureType={}", ex.getClass().getSimpleName());
            throw new BookingException(HttpStatus.SERVICE_UNAVAILABLE, "PNR service is temporarily unavailable. Please retry shortly.");
        }
    }
}
