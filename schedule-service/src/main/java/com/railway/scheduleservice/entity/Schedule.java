package com.railway.scheduleservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity
@Table(name = "schedules", uniqueConstraints = {
        // A train cannot have two stops with the same sequence number
        @UniqueConstraint(columnNames = {"trainNumber", "stopSequence"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String trainNumber;

    @Column(nullable = false, length = 10)
    private String stationCode;

    // Sequence of the stop (1 = Source, 2 = first stop, etc.)
    @Column(nullable = false)
    private Integer stopSequence;

    // Time it arrives at the station (null for source station)
    private LocalTime arrivalTime;

    // Time it leaves the station (null for destination station)
    private LocalTime departureTime;

    // Day of the journey (1 for same day, 2 for next day, etc.)
    @Column(nullable = false)
    private Integer dayNumber;

    // Distance from the source station in kilometers
    private Integer distanceKm;
}