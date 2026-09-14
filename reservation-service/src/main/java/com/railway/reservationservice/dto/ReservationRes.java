package com.railway.reservationservice.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ReservationRes {
    private String pnr;
    private String trainNumber;
    private LocalDate dateOfJourney;
    private LocalDateTime bookingTime;
    private String status;
    private List<PassengerDto> passengers;
}