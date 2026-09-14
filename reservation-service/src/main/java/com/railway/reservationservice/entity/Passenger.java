package com.railway.reservationservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "passengers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Passenger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long passengerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer age;

    @Column(nullable = false)
    private String gender;

    private String birthPreference; // e.g., LOWER, UPPER, SIDE_LOWER

    private String seatNumber; // e.g., S1-45

    @Enumerated(EnumType.STRING)
    private BookingStatus bookingStatus;

    public enum BookingStatus {
        CONFIRMED, RAC, WAITING, CANCELLED
    }
}