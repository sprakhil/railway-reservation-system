package com.railway.paymentservice.client;
import com.railway.paymentservice.dto.*;
import com.railway.paymentservice.exception.PaymentException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class HttpReservationClient implements ReservationClient {
    private final String token;
    private final RestClient client;
    public HttpReservationClient(@Value("${railway.internal.token:}") String token,
            @Value("${railway.reservation.base-url:http://localhost:8085}") String baseUrl) {
        this.token = token;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    private void requireToken() {
        if (token == null || token.isBlank())
            throw new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "Internal service authentication is not configured");
    }
    @Override public ReservationPaymentContext context(String pnr) {
        requireToken();
        try {
            var result = client.get().uri("/internal/reservations/{pnr}/payment-context", pnr)
                    .header("X-Service-Token", token).retrieve().body(ReservationPaymentContext.class);
            if (result == null) throw new IllegalStateException();
            return result;
        } catch (Exception ex) { throw unavailable(); }
    }
    @Override public PaymentConfirmationResult confirm(String pnr, PaymentConfirmation confirmation) {
        requireToken();
        try {
            var result = client.post().uri("/internal/reservations/{pnr}/payment-confirmation", pnr)
                    .header("X-Service-Token", token).contentType(MediaType.APPLICATION_JSON)
                    .body(confirmation).retrieve().body(PaymentConfirmationResult.class);
            if (result == null) throw new IllegalStateException();
            return result;
        } catch (Exception ex) { throw unavailable(); }
    }
    @Override public void refundStatus(String pnr, String paymentId, String status, BigDecimal amount) {
        requireToken();
        try {
            client.post().uri("/internal/reservations/{pnr}/refund-status", pnr)
                    .header("X-Service-Token", token).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("razorpayPaymentId", paymentId, "refundStatus", status, "refundAmount", amount))
                    .retrieve().toBodilessEntity();
        } catch (Exception ex) { throw unavailable(); }
    }
    @Override public void paymentFailed(String pnr) {
        requireToken();
        try {
            client.post().uri("/internal/reservations/{pnr}/payment-failure",pnr)
                .header("X-Service-Token",token).retrieve().toBodilessEntity();
        } catch(Exception ex) { throw unavailable(); }
    }
    private PaymentException unavailable() {
        return new PaymentException(HttpStatus.SERVICE_UNAVAILABLE, "Reservation service is unavailable; payment reconciliation will retry");
    }
}
