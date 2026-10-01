package com.railway.reservationservice.dto;
import java.math.BigDecimal;
import java.time.Instant;
public record PaymentContext(String pnr,String username,BigDecimal totalFare,String currency,String status,
    Instant holdExpiresAt,String razorpayPaymentId,BigDecimal refundAmount,String refundStatus) {}
