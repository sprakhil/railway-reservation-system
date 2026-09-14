package com.railway.trainservice.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long stationId;

    @Column(unique = true, nullable = false, length = 10)
    private String stationCode;

    @Column(nullable = false)
    private String stationName;

    private String city;
    private String state;
    private String zone;
}