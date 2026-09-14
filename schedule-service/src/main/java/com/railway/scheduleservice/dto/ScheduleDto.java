package com.railway.scheduleservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDto {
    private Long id;

    @NotBlank(message = "Train number is required")
    private String trainNumber;

    @NotBlank(message = "Station code is required")
    private String stationCode;

    @NotNull(message = "Stop sequence is required")
    @Min(value = 1, message = "Stop sequence must be at least 1")
    private Integer stopSequence;

    private LocalTime arrivalTime;
    private LocalTime departureTime;

    @NotNull(message = "Day number is required")
    @Min(value = 1, message = "Day number must be at least 1")
    private Integer dayNumber;

    private Integer distanceKm;
}