package com.railway.reservationservice.dto;

import java.util.Map;

/** Counts are booking records (PNRs), not passenger counts. Always scoped to the caller. */
public record BookingOverview(long totalBookings, Map<String,Long> byStatus, ReservationRes nextJourney) {}
