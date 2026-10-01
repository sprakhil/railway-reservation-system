package com.railway.reservationservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

@Data
public class ReservationReq {
    @NotBlank private String fromStation;
    @NotBlank private String toStation;
    @NotBlank private String seatClass;
    @NotNull @jakarta.validation.constraints.DecimalMin("0.01")
    @jakarta.validation.constraints.Digits(integer=10, fraction=2)
    private java.math.BigDecimal expectedTotalFare;
    @NotBlank(message = "Train number is required")
    private String trainNumber;

    @NotNull(message = "Journey date is required")
    private LocalDate dateOfJourney;

    @NotEmpty(message = "At least one passenger is required")
    @jakarta.validation.constraints.Size(max=6)
    @Valid
    private List<@NotNull(message = "Passenger must not be null") PassengerDto> passengers;

    public void setFromStation(String value) {
        fromStation = normalizeStation(value);
    }

    public void setToStation(String value) {
        toStation = normalizeStation(value);
    }

    public static String normalizeStation(String value) {
        return value == null ? null : value.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
