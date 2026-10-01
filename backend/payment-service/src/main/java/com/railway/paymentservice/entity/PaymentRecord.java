package com.railway.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment_records", indexes = @Index(name = "idx_payment_reconcile", columnList = "nextReconcileAt"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentRecord {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Version private Long version;
    @Column(nullable = false, unique = true, length = 10) private String pnr;
    @Column(nullable = false) private String username;
    @Column(unique = true) private String razorpayOrderId;
    // Retained for historical records; all new captures are tracked separately.
    @Column(unique = true) private String razorpayPaymentId;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 3) private String currency;
    @Column(unique = true, length = 40) private String receipt;
    @Enumerated(EnumType.STRING) @Column(length = 32) private PaymentStatus status;
    private Instant holdExpiresAt;
    private Instant orderLeaseUntil;
    private Instant nextReconcileAt;
    private Instant createdAt;
    private Instant updatedAt;
    public enum PaymentStatus { INITIATING, CREATED, CAPTURED, SUCCESS, FAILED }
}
