package com.railway.trainservice.dto;

import com.railway.trainservice.entity.SeatClass;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;

/** Demo fare settings in INR, never a claim about official railway tariffs. */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TrainClassConfigDto {
    @NotNull private SeatClass seatClass;
    @Min(1) @Max(30) private int coachCount;
    @Min(1) @Max(100) private int seatsPerCoach;
    @NotNull @DecimalMin("0.00") @Digits(integer = 7, fraction = 2)
    private BigDecimal baseFare;
    @NotNull @DecimalMin("0.00") @Digits(integer = 7, fraction = 2)
    private BigDecimal ratePerKm;
    @NotNull @DecimalMin("0.00") @Digits(integer = 7, fraction = 2)
    private BigDecimal minimumFare;
}
