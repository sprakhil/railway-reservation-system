package com.railway.reservationservice.controller;

import com.railway.reservationservice.dto.*;
import com.railway.reservationservice.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.Instant;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class BookingApiContractTest {
    @Autowired MockMvc mvc;
    @MockitoBean ReservationService reservations;
    @MockitoBean BookingEngine engine;
    private static final String TOKEN="test-only-internal-token-at-least-32-characters";

    @Test void publicSearchWorksButBookingHistoryRequiresLogin() throws Exception {
        mvc.perform(get("/api/reservations/search").param("fromStation","AAA").param("toStation","CCC")
            .param("dateOfJourney","2030-01-02").param("seatClass","SL")).andExpect(status().isOk());
        mvc.perform(get("/api/reservations/my-bookings")).andExpect(status().isUnauthorized());
    }
    @Test void bookingRequiresAnIdempotencyKeyAndValidPassengers() throws Exception {
        String body="""
            {"trainNumber":"12345","fromStation":"AAA","toStation":"CCC","seatClass":"SL",
            "dateOfJourney":"2030-01-02","expectedTotalFare":120,
            "passengers":[{"name":"Alice","age":25,"gender":"FEMALE"}]}
            """;
        mvc.perform(post("/api/reservations/book").with(user("alice").roles("PASSENGER"))
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/reservations/book").with(user("alice").roles("PASSENGER"))
            .header("Idempotency-Key","request_123").contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("\"age\":25","\"age\":null"))).andExpect(status().isBadRequest());
        verifyNoInteractions(reservations);
    }
    @Test void ownerAndAdminIdentityArePassedFromAuthentication() throws Exception {
        when(reservations.getReservationByPnr("1234567890","alice",false)).thenReturn(ReservationRes.builder().pnr("1234567890").status("CONFIRMED").build());
        mvc.perform(get("/api/reservations/pnr/1234567890").with(user("alice").roles("PASSENGER")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(delete("/api/reservations/1234567890").with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        verify(reservations).cancelReservation("1234567890","admin",true);
    }
    @Test void internalContextRequiresServiceTokenAndSerializesPaymentContract() throws Exception {
        mvc.perform(get("/internal/reservations/1234567890/payment-context")).andExpect(status().isForbidden());
        mvc.perform(get("/internal/reservations/1234567890/payment-context").header("X-Service-Token","wrong"))
            .andExpect(status().isForbidden());
        when(engine.paymentContext("1234567890")).thenReturn(new PaymentContext("1234567890","alice",new BigDecimal("120.00"),"INR",
            "PENDING_PAYMENT",Instant.parse("2030-01-01T00:05:00Z"),null,BigDecimal.ZERO,"NONE"));
        mvc.perform(get("/internal/reservations/1234567890/payment-context").header("X-Service-Token",TOKEN))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalFare").value(120))
            .andExpect(jsonPath("$.holdExpiresAt").value("2030-01-01T00:05:00Z"));
    }
    @Test void internalConfirmationAndRefundBodiesMatchPaymentClient() throws Exception {
        when(engine.confirm(eq("1234567890"),any())).thenReturn(new PaymentDecision("1234567890","EXPIRED",true,new BigDecimal("120.00")));
        mvc.perform(post("/internal/reservations/1234567890/payment-confirmation").header("X-Service-Token",TOKEN)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"razorpayOrderId":"order_test","razorpayPaymentId":"pay_test","amount":120.00,"currency":"INR"}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.refundRequired").value(true));
        mvc.perform(post("/internal/reservations/1234567890/refund-status").header("X-Service-Token",TOKEN)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"razorpayPaymentId":"pay_test","refundStatus":"PROCESSED","refundAmount":120.00}
                """))
            .andExpect(status().isOk());
        verify(engine).refundStatus(eq("1234567890"),argThat(u->u.refundStatus().equals("PROCESSED")));
    }
    @Test void malformedBearerTokenIsRejected() throws Exception {
        mvc.perform(get("/api/reservations/my-bookings").header("Authorization","Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }
}
