package com.railway.reservationservice.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="booking_notification_outbox") @Data @NoArgsConstructor
public class BookingNotification {
    @Column(length=40) private String supportTicketId;
    @Id private String eventId;
    @Lob @Column(columnDefinition="LONGTEXT") private String bookingJson;
    private String pnr;
    private String eventType;
    @Column(length=4000) private String message;
    private String userEmail;
    private String username;
    private String trainNumber;
    private String dateOfJourney;
    private java.time.Instant nextAttemptAt;
    private java.time.Instant deliveredAt;
    private int attempts;
}
