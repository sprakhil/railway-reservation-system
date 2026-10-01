package com.railway.reservationservice.dto;
import java.math.BigDecimal;
import jakarta.validation.constraints.*;
public record PaymentConfirmation(@NotBlank String razorpayOrderId,@NotBlank String razorpayPaymentId,
    @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal amount,@Pattern(regexp="INR") @NotBlank String currency) {}
