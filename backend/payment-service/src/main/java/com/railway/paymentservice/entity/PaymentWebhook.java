package com.railway.paymentservice.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name = "payment_webhooks", indexes = @Index(name = "idx_webhook_reconcile", columnList = "processed,nextAttemptAt"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentWebhook {
    // Hash the signed body; an unsigned event-id header must not control deduplication.
    @Id @Column(length = 64) private String bodyHash;
    @Version private Long version;
    @Lob @Column(nullable = false) private String body;
    private boolean processed;
    private Instant nextAttemptAt;
    private Instant receivedAt;
}
