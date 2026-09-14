package com.railway.reservationservice.repository;

import com.railway.reservationservice.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    Optional<Reservation> findByPnr(String pnr);
    List<Reservation> findByUsername(String username);
}