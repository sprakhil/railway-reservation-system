package com.railway.trainservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrainClassConfiguration {
    @Enumerated(EnumType.STRING) @Column(name = "seat_class", nullable = false)
    private SeatClass seatClass;
    @Column(nullable = false) private int coachCount;
    @Column(nullable = false) private int seatsPerCoach;
    @Column(nullable = false, precision = 9, scale = 2) private BigDecimal baseFare;
    @Column(nullable = false, precision = 9, scale = 2) private BigDecimal ratePerKm;
    @Column(nullable = false, precision = 9, scale = 2) private BigDecimal minimumFare;
}
