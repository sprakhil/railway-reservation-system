package com.railway.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderRes {
    private String razorpayOrderId;
    private String pnr;
    private BigDecimal amount;
    private String currency;
    private String status;
}