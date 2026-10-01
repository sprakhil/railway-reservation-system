package com.railway.trainservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor(onConstructor_ = @com.fasterxml.jackson.annotation.JsonCreator)
@AllArgsConstructor
public class StationDto {
    @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
    private Long stationId;

    @NotBlank(message = "Station code is required")
    @Pattern(regexp = "[A-Z0-9]{1,10}", message = "Use 1-10 uppercase letters or digits")
    private String stationCode;

    public void setStationCode(String value) {
        stationCode = value == null ? null : value.strip().toUpperCase(java.util.Locale.ROOT);
    }

    @NotBlank(message = "Station name is required")
    @Size(max = 255)
    private String stationName;

    @Size(max = 255) private String city;
    @Size(max = 255) private String state;
    @Size(max = 255) private String zone;
    @jakarta.validation.constraints.DecimalMin("-90") @jakarta.validation.constraints.DecimalMax("90")
    private Double latitude;
    @jakarta.validation.constraints.DecimalMin("-180") @jakarta.validation.constraints.DecimalMax("180")
    private Double longitude;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @jakarta.validation.constraints.AssertTrue(message="Provide both latitude and longitude, or leave both empty")
    public boolean isCoordinatesValid() {
        return latitude==null && longitude==null || latitude!=null && longitude!=null
            && Double.isFinite(latitude) && Double.isFinite(longitude);
    }
}
