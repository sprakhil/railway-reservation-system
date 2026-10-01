package com.railway.reservationservice.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@lombok.NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator)
@lombok.AllArgsConstructor
public class ReservationRes {
    private String trainName;
    private java.math.BigDecimal cancellationFee;
    private String fromStation;
    private String toStation;
    private String seatClass;
    private LocalDate runDate;
    private java.time.Instant departureAt;
    private java.time.Instant holdExpiresAt;
    private java.time.Instant waitlistDeadline;
    private String allocation;
    private Integer waitlistPosition;
    private java.math.BigDecimal farePerPassenger;
    private java.math.BigDecimal baseFare;
    private java.math.BigDecimal ratePerKm;
    private java.math.BigDecimal minimumFare;
    private Integer distanceKm;
    private java.math.BigDecimal totalFare;
    private String currency;
    private String paymentStatus;
    private String refundStatus;
    private java.math.BigDecimal refundAmount;
    private boolean legacyBooking;
    private boolean testMode;
    private String pnr;
    private String trainNumber;
    private LocalDate dateOfJourney;
    private LocalDateTime bookingTime;
    private String status;
    private List<PassengerDto> passengers;
}
