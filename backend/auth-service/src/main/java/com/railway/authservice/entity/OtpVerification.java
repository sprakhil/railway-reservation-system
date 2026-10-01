package com.railway.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "otp_verifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    // Password-encoded OTP; never store or log the six-digit value.
    @Column(nullable = false, length = 255)
    private String otp;

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Purpose purpose;

    @Column(nullable = false)
    private int failedAttempts;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum Purpose { REGISTRATION, PASSWORD_RESET }
}
