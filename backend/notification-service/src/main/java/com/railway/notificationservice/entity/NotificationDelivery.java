package com.railway.notificationservice.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="notification_deliveries") @Getter @Setter @NoArgsConstructor
public class NotificationDelivery {
 @Id @Column(length=150) private String eventId;
 @Lob @Column(columnDefinition="LONGTEXT") private String payloadJson;
 private String pnr; private String userEmail; private String username; private String trainNumber; private String dateOfJourney; private String eventType;
 @Column(length=4000) private String message;
 private int attempts; private Instant nextAttemptAt; private Instant sentAt;
}
