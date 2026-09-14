package com.railway.trainservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationDto {
    private Long stationId;

    @NotBlank(message = "Station code is required")
    private String stationCode;

    @NotBlank(message = "Station name is required")
    private String stationName;

    private String city;
    private String state;
    private String zone;
}