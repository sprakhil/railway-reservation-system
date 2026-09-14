package com.railway.reservationservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class ReservationReq {
    @NotBlank(message = "Train number is required")
    private String trainNumber;

    @NotNull(message = "Journey date is required")
    @FutureOrPresent(message = "Journey date must be today or in the future")
    private LocalDate dateOfJourney;

    @NotEmpty(message = "At least one passenger is required")
    @Valid
    private List<PassengerDto> passengers;
}