package com.railway.reservationservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassengerDto {
    @NotBlank(message = "Passenger name is required")
    private String name;

    @Min(value = 1, message = "Age must be valid")
    private Integer age;

    @NotBlank(message = "Gender is required")
    private String gender;

    private String birthPreference;

    // These fields are populated by the backend, ignored in requests
    private String seatNumber;
    private String bookingStatus;
}