package com.railway.userservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "user_profiles")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // This unique field links this profile to the AuthUser in the Auth Service
    @Column(unique = true, nullable = false)
    private String username;

    private String firstName;
    private String lastName;
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private LocalDate dateOfBirth;

    @Column(columnDefinition = "TEXT")
    private String address;

    public enum Gender {
        MALE, FEMALE, OTHER
    }
}