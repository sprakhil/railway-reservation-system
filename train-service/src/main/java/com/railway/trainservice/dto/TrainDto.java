package com.railway.trainservice.dto;

import com.railway.trainservice.entity.SeatClass;
import com.railway.trainservice.entity.TrainCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainDto {
    private Long trainId;

    @NotBlank(message = "Train number is required")
    private String trainNumber;

    @NotBlank(message = "Train name is required")
    private String trainName;

    @NotNull(message = "Train category is required")
    private TrainCategory category;

    @NotNull(message = "Source station ID is required")
    private Long sourceStationId;

    @NotNull(message = "Destination station ID is required")
    private Long destinationStationId;

    @NotEmpty(message = "At least one seat class must be provided")
    private Set<SeatClass> seatClasses;
}