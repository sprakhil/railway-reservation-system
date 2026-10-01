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
    @jakarta.validation.constraints.Size(max=100)
    private String name;

    @Min(value = 1, message = "Age must be valid")
    @jakarta.validation.constraints.Max(120)
    @jakarta.validation.constraints.NotNull
    private Integer age;

    @NotBlank(message = "Gender is required")
    @jakarta.validation.constraints.Pattern(regexp="MALE|FEMALE|OTHER")
    private String gender;

    @jakarta.validation.constraints.Size(max=20)
    @jakarta.validation.constraints.Pattern(regexp="LOWER|MIDDLE|UPPER|SIDE_LOWER|SIDE_UPPER|NONE",
        message="Choose LOWER, MIDDLE, UPPER, SIDE_LOWER, SIDE_UPPER or NONE")
    private String birthPreference;

    // These fields are populated by the backend, ignored in requests
    @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
    private String seatNumber;
    @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
    private SeatDetails seatDetails;
    @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
    private String bookingStatus;
}
