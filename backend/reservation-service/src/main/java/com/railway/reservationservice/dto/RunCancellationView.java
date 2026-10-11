package com.railway.reservationservice.dto;
import java.time.*;
import java.math.BigDecimal;
public record RunCancellationView(String trainNumber, LocalDate runDate, Instant sourceDepartureAt,
    boolean cancelled, String reason, String cancelledBy, Instant cancelledAt,
    long bookings, long passengers, long confirmedBookings, long waitingBookings, long unpaidBookings,
    BigDecimal refundAmount, BigDecimal processedRefunds, BigDecimal outstandingRefunds) {}
