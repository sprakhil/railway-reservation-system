package com.railway.scheduleservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator)
@AllArgsConstructor
public class ScheduleDto {
    @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
    private Long id;

    @Pattern(regexp = "[A-Z0-9]{1,10}")
    private String trainNumber;

    @NotBlank(message = "Station code is required")
    @Pattern(regexp = "[A-Z0-9]{1,10}")
    private String stationCode;

    public void setStationCode(String value) {
        stationCode = value == null ? null : value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    @NotNull(message = "Stop sequence is required")
    @Min(value = 1, message = "Stop sequence must be at least 1")
    private Integer stopSequence;

    private LocalTime arrivalTime;
    private LocalTime departureTime;

    @NotNull(message = "Day number is required")
    @Min(value = 1, message = "Day number must be at least 1")
    @Max(30)
    private Integer dayNumber;

    @Min(1) @Max(30)
    private Integer departureDayNumber;

    @NotNull @Min(0) @Max(20000)
    private Integer distanceKm;
}
