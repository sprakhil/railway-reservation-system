package com.railway.reservationservice.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name="seat_inventory") @Data @NoArgsConstructor
public class SeatInventory {
    @Id @Column(length=100) private String inventoryKey;
    private int capacity;
}
