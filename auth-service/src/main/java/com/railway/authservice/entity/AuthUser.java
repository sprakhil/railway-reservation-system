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

    private String providerId;

    private boolean emailVerified;

    @Column(nullable = false)
    @Builder.Default
    private boolean isVerified = false;

    public enum Role { ADMIN, PASSENGER }
    public enum Status { ACTIVE, INACTIVE, BLOCKED, DELETED }
    public enum Provider { LOCAL, GOOGLE }
}