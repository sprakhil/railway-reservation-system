package com.railway.reservationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name="support_messages",indexes=@Index(name="idx_support_message_ticket",columnList="ticketId,createdAt,id"))
@Getter @Setter @NoArgsConstructor
public class SupportMessage {
    @Id @Column(length=90) private String id;
    @Column(nullable=false,length=40) private String ticketId;
    @Column(nullable=false) private String author;
    @Column(nullable=false) private boolean staff;
    @Column(nullable=false) private boolean systemMessage;
    @Column(nullable=false,length=3000) private String body;
    @Column(nullable=false) private Instant createdAt;
}
