package com.railway.reservationservice.dto;
import java.time.LocalTime;
import lombok.Data;
@Data
public class CatalogStop {
    private String stationCode;
    private Integer stopSequence;
    private LocalTime arrivalTime;
    private LocalTime departureTime;
    private Integer dayNumber;
    private Integer departureDayNumber;
    private Integer distanceKm;
}
