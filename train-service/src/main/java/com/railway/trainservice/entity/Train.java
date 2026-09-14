package com.railway.trainservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.Set;

@Entity
@Table(name = "trains")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Train {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long trainId;

    @Column(unique = true, nullable = false, length = 10)
    private String trainNumber;

    @Column(nullable = false)
    private String trainName;

    @Enumerated(EnumType.STRING)
    private TrainCategory category;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "source_station_id", nullable = false)
    private Station sourceStation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "destination_station_id", nullable = false)
    private Station destinationStation;

    // A train can have multiple seat classes (e.g., SL, 3A, 2A)
    @ElementCollection(targetClass = SeatClass.class, fetch = FetchType.EAGER)
    @CollectionTable(name = "train_seat_classes", joinColumns = @JoinColumn(name = "train_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "seat_class")
    private Set<SeatClass> seatClasses;
}