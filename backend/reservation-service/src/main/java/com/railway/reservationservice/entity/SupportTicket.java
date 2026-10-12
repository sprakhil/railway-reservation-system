package com.railway.reservationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="support_tickets", indexes={
    @Index(name="idx_support_owner_updated",columnList="username,updatedAt"),
    @Index(name="idx_support_status_updated",columnList="status,updatedAt")
}) @Getter @Setter @NoArgsConstructor
public class SupportTicket {
    public enum Category { BOOKING, PAYMENT_REFUND, ACCOUNT, OTHER }
    public enum Status { OPEN, IN_PROGRESS, RESOLVED }
    @Id @Column(length=40) private String id;
    @Column(nullable=false) private String username;
    @Column(nullable=false) private String userEmail;
    @Column(nullable=false,length=160) private String subject;
    @Column(length=10) private String pnr;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Category category;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=30) private Status status;
    @Column(nullable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    @Column(nullable=false,length=64) private String requestHash;
    @Version private long version;
}
