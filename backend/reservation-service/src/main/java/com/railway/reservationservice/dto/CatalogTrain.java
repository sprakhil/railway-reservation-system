package com.railway.reservationservice.dto;
import lombok.Data;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;
@Data
public class CatalogTrain {
    private String trainNumber;
    private String trainName;
    private Set<String> seatClasses;
    private Set<DayOfWeek> operatingDays;
    private List<ClassConfiguration> classConfigurations;
    @Data public static class ClassConfiguration {
        private String seatClass;
        private int coachCount;
        private int seatsPerCoach;
        private BigDecimal baseFare;
        private BigDecimal ratePerKm;
        private BigDecimal minimumFare;
    }
}
