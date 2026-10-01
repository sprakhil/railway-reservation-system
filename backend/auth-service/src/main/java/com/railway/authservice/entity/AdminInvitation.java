package com.railway.authservice.entity;
import jakarta.persistence.*; import lombok.*; import java.time.Instant;
@Entity @Table(name="admin_invitations") @Getter @Setter @NoArgsConstructor
public class AdminInvitation {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=64) private String tokenHash;
 @Column(nullable=false) private String email;
 @Column(nullable=false) private String invitedBy;
 private String acceptedBy; private Instant createdAt; private Instant expiresAt; private Instant acceptedAt; private Instant revokedAt;
}
