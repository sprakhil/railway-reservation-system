package com.railway.paymentservice.dto;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentOrderRes {
    private String razorpayOrderId;
    private String pnr;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String keyId;
    private long amountInPaise;
    private Instant holdExpiresAt;
    private String mode;
}
