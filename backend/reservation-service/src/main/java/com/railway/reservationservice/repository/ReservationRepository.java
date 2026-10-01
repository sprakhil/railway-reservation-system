package com.railway.reservationservice.repository;

import com.railway.reservationservice.entity.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Reservation> {
    List<Reservation> findByInventoryKeyAndStatusInOrderByReservationIdAsc(String key, java.util.Collection<Reservation.ReservationStatus> statuses);
    @org.springframework.data.jpa.repository.Query("select distinct r.inventoryKey from Reservation r where r.inventoryKey is not null and r.status in (com.railway.reservationservice.entity.Reservation.ReservationStatus.PENDING_PAYMENT, com.railway.reservationservice.entity.Reservation.ReservationStatus.WAITING) order by r.inventoryKey")
    List<String> maintenanceKeys();
    @org.springframework.data.jpa.repository.Query("select distinct r.inventoryKey from Reservation r where r.username=:username and r.inventoryKey is not null and r.status in (com.railway.reservationservice.entity.Reservation.ReservationStatus.PENDING_PAYMENT, com.railway.reservationservice.entity.Reservation.ReservationStatus.WAITING) order by r.inventoryKey")
    List<String> userMaintenanceKeys(@org.springframework.data.repository.query.Param("username") String username);
    Optional<Reservation> findByBookingKey(String bookingKey);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from Reservation r where r.pnr = :pnr")
    Optional<Reservation> lockByPnr(@org.springframework.data.repository.query.Param("pnr") String pnr);
    @org.springframework.data.jpa.repository.Query("select distinct r from Reservation r left join fetch r.passengers where r.inventoryKey = :key and r.fromSequence < :toSeq and r.toSequence > :fromSeq and (r.status = com.railway.reservationservice.entity.Reservation.ReservationStatus.CONFIRMED or (r.status = com.railway.reservationservice.entity.Reservation.ReservationStatus.PENDING_PAYMENT and r.holdExpiresAt > :now))")
    List<Reservation> activeOverlapping(@org.springframework.data.repository.query.Param("key") String key,
        @org.springframework.data.repository.query.Param("fromSeq") int fromSeq,
        @org.springframework.data.repository.query.Param("toSeq") int toSeq,
        @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    List<Reservation> findTop100ByStatusAndHoldExpiresAtLessThanEqual(Reservation.ReservationStatus status, java.time.Instant now);
    Optional<Reservation> findByPnr(String pnr);
    List<Reservation> findByUsername(String username);
    long countByUsername(String username);
    long countByUsernameAndStatus(String username, Reservation.ReservationStatus status);
    Optional<Reservation> findFirstByUsernameAndStatusInAndDepartureAtAfterOrderByDepartureAtAscReservationIdAsc(
        String username, java.util.Collection<Reservation.ReservationStatus> statuses, java.time.Instant now);
}
