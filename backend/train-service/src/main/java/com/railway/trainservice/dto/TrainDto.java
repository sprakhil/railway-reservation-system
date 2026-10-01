package com.railway.trainservice.dto;

import com.railway.trainservice.entity.SeatClass;
import com.railway.trainservice.entity.TrainCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TrainDto {
    @io.swagger.v3.oas.annotations.media.Schema(accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
    private Long trainId;
    @NotBlank @Pattern(regexp = "[A-Z0-9]{1,10}", message = "Use 1-10 uppercase letters or digits")
    private String trainNumber;
    @NotBlank @Size(max = 255) private String trainName;
    @NotNull private TrainCategory category;
    @NotNull @Positive private Long sourceStationId;
    @NotNull @Positive private Long destinationStationId;
    @NotEmpty private Set<@NotNull SeatClass> seatClasses;
    @NotEmpty(message = "At least one operating day is required")
    private Set<@NotNull DayOfWeek> operatingDays;
    @Valid
    private List<@NotNull @Valid TrainClassConfigDto> classConfigurations;
}
