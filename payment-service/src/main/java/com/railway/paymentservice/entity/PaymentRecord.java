package com.railway.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String pnr;

    @Column(nullable = false)
    private String username;

    @Column(unique = true, nullable = false)
    private String razorpayOrderId;

    @Column(unique = true)
    private String razorpayPaymentId;

    @Column(nullable = false)
    private BigDecimal amount;

    private String currency;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum PaymentStatus {
        CREATED, SUCCESS, FAILED
    }
}