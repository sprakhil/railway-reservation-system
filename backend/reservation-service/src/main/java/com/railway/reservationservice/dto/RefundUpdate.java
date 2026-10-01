package com.railway.reservationservice.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record RefundUpdate(@NotBlank String razorpayPaymentId,
    @NotBlank @Pattern(regexp="PENDING|PROCESSING|PROCESSED|COMPLETED|FAILED|REFUNDED") String refundStatus,
    @NotNull @DecimalMin("0.00") BigDecimal refundAmount) {}
