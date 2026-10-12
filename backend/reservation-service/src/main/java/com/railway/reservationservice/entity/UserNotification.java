package com.railway.reservationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/** A durable, private inbox entry, independent of email delivery retries. */
@Entity @Table(name="user_notifications", indexes={
    @Index(name="idx_notification_owner_created", columnList="username,createdAt,eventId"),
    @Index(name="idx_notification_owner_read", columnList="username,readAt")
}) @Data @NoArgsConstructor
public class UserNotification {
    @Id @Column(length=120) private String eventId;
    @Column(nullable=false) private String username;
    private String pnr;
    @Column(length=40) private String supportTicketId;
    @Column(nullable=false) private String eventType;
    @Column(nullable=false) private String title;
    @Column(nullable=false,length=4000) private String message;
    @Column(nullable=false) private Instant createdAt;
    private Instant readAt;
}
