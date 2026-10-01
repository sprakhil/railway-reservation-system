package com.railway.paymentservice.controller;

import com.railway.paymentservice.dto.*;
import com.railway.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {
    @Mock PaymentService service;
    @InjectMocks PaymentController controller;
    private final UsernamePasswordAuthenticationToken passenger = new UsernamePasswordAuthenticationToken(
            "alice", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    private final PaymentStatusRes status = new PaymentStatusRes("1234567890", "order_1",
            new BigDecimal("125.50"), "INR", "SUCCESS", "CONFIRMED", null, "TEST", List.of());

    @Test void createOrderPassesAuthenticatedUsernameAndReturnsCreated() {
        var request = new PaymentOrderReq("1234567890", null);
        var order = PaymentOrderRes.builder().pnr(request.getPnr()).razorpayOrderId("order_1").mode("TEST").build();
        when(service.createOrder("alice", request)).thenReturn(order);
        var response = controller.createOrder(request, passenger);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertSame(order, response.getBody());
        verify(service).createOrder("alice", request);
    }

    @Test void verifyPaymentPassesAuthenticatedUsernameAndReturnsStructuredStatus() {
        var request = new PaymentVerificationReq("order_1", "pay_1", "signature");
        when(service.verifyPayment("alice", request)).thenReturn(status);
        var response = controller.verifyPayment(request, passenger);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(status, response.getBody());
        verify(service).verifyPayment("alice", request);
    }

    @Test void statusPassesActualAdminAuthority() {
        when(service.status("alice", false, "1234567890")).thenReturn(status);
        assertSame(status, controller.status("1234567890", passenger));
        var admin = new UsernamePasswordAuthenticationToken("admin", null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(service.status("admin", true, "1234567890")).thenReturn(status);
        assertSame(status, controller.status("1234567890", admin));
    }

    @Test void webhookPassesExactSignedBytesAndReturnsAcceptedOnlyAfterEnqueue() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var response = controller.webhook(body, "signature");
        verify(service).webhook(body, "signature");
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("TEST", response.getBody().get("mode"));
    }
}
