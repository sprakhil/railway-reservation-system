package com.railway.pnrservice.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "pnr_records")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PnrRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The 10-digit PNR must be universally unique
    @Column(unique = true, nullable = false, length = 10)
    private String pnrNumber;

    @Column(nullable = false)
    private LocalDateTime generatedAt;
}