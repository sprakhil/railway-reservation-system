package com.railway.paymentservice.dto;
import java.math.BigDecimal;
public record PaymentConfirmationResult(String pnr, String status, boolean refundRequired, BigDecimal refundAmount) {}
