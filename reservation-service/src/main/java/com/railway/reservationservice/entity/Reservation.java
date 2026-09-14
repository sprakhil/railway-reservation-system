package com.railway.reservationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reservationId;

    @Column(unique = true, nullable = false, length = 10)
    private String pnr;

    @Column(nullable = false)
    private String username; // Links to the AuthUser

    @Column(nullable = false, length = 10)
    private String trainNumber;

    @Column(nullable = false)
    private LocalDate dateOfJourney;

    @Column(nullable = false)
    private LocalDateTime bookingTime;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    private double totalFare;

    // CascadeType.ALL ensures when a Reservation is saved, its Passengers are saved too
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Passenger> passengers = new ArrayList<>();

    public enum ReservationStatus {
        CONFIRMED, RAC, WAITING, CANCELLED
    }

    // Helper method to synchronize bidirectional relationship
    public void addPassenger(Passenger passenger) {
        passengers.add(passenger);
        passenger.setReservation(this);
    }
}