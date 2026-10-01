package com.railway.paymentservice.entity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

/** One row per captured provider payment, including unexpected duplicate captures. */
@Entity @Table(name = "payment_captures")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentCapture {
    @Id @Column(length = 64) private String paymentId;
    @Version private Long version;
    @Column(nullable = false, length = 10) private String pnr;
    @Column(nullable = false, length = 64) private String orderId;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(nullable = false) private boolean confirmed;
    @Column(precision = 12, scale = 2) private BigDecimal refundAmount;
    @Column(unique = true, length = 40) private String refundKey;
    @Column(unique = true, length = 64) private String refundId;
    @Column(length = 32) private String refundStatus;
    private Instant createdAt;
    private Instant updatedAt;
}
