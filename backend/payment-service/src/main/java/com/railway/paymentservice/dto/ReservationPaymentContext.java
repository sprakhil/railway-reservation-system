package com.railway.paymentservice.dto;
import java.math.BigDecimal;
import java.time.Instant;
public record ReservationPaymentContext(String pnr, String username, BigDecimal totalFare,
        String currency, String status, Instant holdExpiresAt, String razorpayPaymentId,
        BigDecimal refundAmount, String refundStatus) {}
