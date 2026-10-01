package com.railway.paymentservice.dto;
import java.math.BigDecimal;
public record PaymentConfirmation(String razorpayOrderId, String razorpayPaymentId, BigDecimal amount, String currency) {}
