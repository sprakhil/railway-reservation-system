package com.railway.userservice.dto;
import jakarta.validation.constraints.*;
public record SavedPassengerRequest(@NotBlank @Size(max=100) String name,@NotNull @Min(1) @Max(120) Integer age,
 @NotBlank @Pattern(regexp="MALE|FEMALE|OTHER") String gender,
 @Pattern(regexp="LOWER|MIDDLE|UPPER|SIDE_LOWER|SIDE_UPPER|NONE") String berthPreference) {}
