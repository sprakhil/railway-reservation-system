package com.railway.reservationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.*;

/** A permanent operational decision; train catalogue and historical tickets remain intact. */
@Entity @Table(name="train_run_cancellations") @Getter @Setter @NoArgsConstructor
public class TrainRunCancellation {
    @Id @Column(length=80) private String runKey;
    @Column(nullable=false,length=10) private String trainNumber;
    @Column(nullable=false) private LocalDate runDate;
    @Column(nullable=false) private Instant sourceDepartureAt;
    @Column(nullable=false,length=500) private String reason;
    @Column(nullable=false) private String cancelledBy;
    @Column(nullable=false) private Instant cancelledAt;
}
