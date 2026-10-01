package com.railway.reservationservice.dto;
import java.math.BigDecimal;
import java.time.*;
import lombok.*;
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class JourneyQuote {
    private String trainNumber;
    private String trainName;
    private String fromStation;
    private String toStation;
    private String seatClass;
    private LocalDate boardingDate;
    private LocalDate runDate;
    private Instant departureAt;
    private Instant arrivalAt;
    private int fromSequence;
    private int toSequence;
    private int distanceKm;
    private int passengerCount;
    private int coachCount;
    private int seatsPerCoach;
    private BigDecimal baseFare;
    private BigDecimal ratePerKm;
    private BigDecimal minimumFare;
    private BigDecimal farePerPassenger;
    private BigDecimal totalFare;
    @Builder.Default private String currency = "INR";
    @Builder.Default private boolean testMode = true;
    @Builder.Default private String pricingPolicy = "CONFIGURABLE_DEMO_FARES";
    private int availableSeats;
    private String suggestedAllocation;
    private Instant waitlistDeadline;
    private boolean waitlistOpen;
    public String inventoryKey() { return trainNumber + "|" + runDate + "|" + seatClass; }
    public int capacity() { return Math.multiplyExact(coachCount, seatsPerCoach); }
}
