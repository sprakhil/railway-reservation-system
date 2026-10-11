package com.railway.reservationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "reservations", indexes = {
    @Index(name="idx_reservation_inventory",columnList="inventory_key,from_sequence,to_sequence,status,hold_expires_at"),
    @Index(name="idx_reservation_username",columnList="username"),
    @Index(name="idx_reservation_run",columnList="train_number,run_date")
})
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
    private String trainName;

    @Column(nullable = false)
    private LocalDate dateOfJourney;

    @Column(nullable = false)
    private LocalDateTime bookingTime;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    // Keep old total_fare column untouched: legacy rows did not contain reliable fares.
    @Column(name="quoted_total_fare", precision=12, scale=2)
    private java.math.BigDecimal totalFare;
    @Column(precision=12, scale=2) private java.math.BigDecimal farePerPassenger;
    @Column(precision=12, scale=2) private java.math.BigDecimal baseFare;
    @Column(precision=12, scale=2) private java.math.BigDecimal ratePerKm;
    @Column(precision=12, scale=2) private java.math.BigDecimal minimumFare;
    @Column(precision=12, scale=2) private java.math.BigDecimal cancellationFee;
    @Column(precision=12, scale=2) private java.math.BigDecimal refundAmount;
    private String refundStatus;
    @Column(length=500) private String operatorCancellationReason;
    private java.time.Instant operatorCancelledAt;
    private String operatorPreviousStatus;
    private String currency;
    private String userEmail;
    private String fromStation;
    private String toStation;
    private String seatClass;
    private LocalDate runDate;
    private Integer fromSequence;
    private Integer toSequence;
    private Integer distanceKm;
    private java.time.Instant departureAt;
    private java.time.Instant holdExpiresAt;
    private java.time.Instant waitlistDeadline;
    private boolean waitlistRequested;
    private Integer coachCount;
    private Integer seatsPerCoach;
    private String inventoryKey;
    @Column(unique=true, length=180) private String bookingKey;
    private String requestHash;
    @Column(unique=true) private String razorpayPaymentId;
    private String razorpayOrderId;
    @Column(columnDefinition="integer default 0") private int dataVersion;
    @Version @Column(columnDefinition="bigint default 0") private Long version;

    // CascadeType.ALL ensures when a Reservation is saved, its Passengers are saved too
    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Passenger> passengers = new ArrayList<>();

    public enum ReservationStatus {
        PENDING_PAYMENT, CONFIRMED, EXPIRED, WAITING, CANCELLED
    }

    // Helper method to synchronize bidirectional relationship
    public void addPassenger(Passenger passenger) {
        passengers.add(passenger);
        passenger.setReservation(this);
    }
}
