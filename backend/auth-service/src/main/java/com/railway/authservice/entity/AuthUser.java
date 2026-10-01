package com.railway.authservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@SQLDelete(sql = "UPDATE users SET status = 'DELETED' WHERE id=?")
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    // The original username remains the cross-service ownership key. This is the editable login/display name.
    @Column(unique = true, length = 20)
    private String publicUsername;

    private java.time.Instant usernameChangedAt;

    public String displayUsername() { return publicUsername == null ? username : publicUsername; }

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    @Enumerated(EnumType.STRING)
    private Role role; // ADMIN, PASSENGER

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.ACTIVE; // <-- Set the default value

    @Enumerated(EnumType.STRING)
    private Provider provider; // LOCAL, GOOGLE

    @Column(unique = true)
    private String providerId;

    private boolean emailVerified;

    @Column(nullable = false)
    @Builder.Default
    private boolean isVerified = false;

    public enum Role { ADMIN, PASSENGER }
    public enum Status { ACTIVE, INACTIVE, BLOCKED, DELETED }
    public enum Provider { LOCAL, GOOGLE }
}
