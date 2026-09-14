package com.railway.paymentservice.controller;

import com.railway.paymentservice.dto.PaymentOrderReq;
import com.railway.paymentservice.dto.PaymentOrderRes;
import com.railway.paymentservice.dto.PaymentVerificationReq;
import com.railway.paymentservice.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private PaymentController paymentController;

    @Test
    void createOrder_ShouldReturn201AndOrderDetails() {
        // Arrange
        String username = "johndoe";
        PaymentOrderReq req = new PaymentOrderReq();
        req.setPnr("PNR1234567");
        req.setAmount(new BigDecimal("1500.00"));

        PaymentOrderRes mockRes = PaymentOrderRes.builder()
                .razorpayOrderId("order_abc123")
                .pnr("PNR1234567")
                .amount(new BigDecimal("1500.00"))
                .currency("INR")
                .status("CREATED")
                .build();

        when(authentication.getName()).thenReturn(username);
        when(paymentService.createOrder(eq(username), any(PaymentOrderReq.class))).thenReturn(mockRes);

        // Act
        ResponseEntity<PaymentOrderRes> response = paymentController.createOrder(req, authentication);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("order_abc123", response.getBody().getRazorpayOrderId());
    }

    @Test
    void verifyPayment_ShouldReturn200AndSuccessMessage() {
        // Arrange
        PaymentVerificationReq req = new PaymentVerificationReq();
        req.setRazorpayOrderId("order_abc123");
        req.setRazorpayPaymentId("pay_def456");
        req.setRazorpaySignature("signature_hash");

        when(paymentService.verifyPayment(any(PaymentVerificationReq.class))).thenReturn("Payment Successful");

        // Act
        ResponseEntity<String> response = paymentController.verifyPayment(req);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Payment Successful", response.getBody());
    }
}