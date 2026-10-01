package com.railway.reservationservice.dto;
import java.math.BigDecimal;
public record PaymentDecision(String pnr,String status,boolean refundRequired,BigDecimal refundAmount) {}
