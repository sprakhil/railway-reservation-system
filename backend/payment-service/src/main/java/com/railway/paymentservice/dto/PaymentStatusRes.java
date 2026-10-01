package com.railway.paymentservice.dto;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public record PaymentStatusRes(String pnr, String razorpayOrderId, BigDecimal amount,
        String currency, String paymentStatus, String bookingStatus, Instant holdExpiresAt,
        String mode, List<CaptureStatus> payments) {
    public record CaptureStatus(String razorpayPaymentId, boolean bookingConfirmed,
            BigDecimal refundAmount, String refundStatus, String razorpayRefundId) {}
}
