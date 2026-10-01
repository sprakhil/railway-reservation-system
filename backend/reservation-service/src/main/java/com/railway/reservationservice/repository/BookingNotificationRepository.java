package com.railway.reservationservice.repository;
import com.railway.reservationservice.entity.BookingNotification;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface BookingNotificationRepository extends JpaRepository<BookingNotification,String> {
    List<BookingNotification> findTop50ByDeliveredAtIsNullAndNextAttemptAtLessThanEqual(Instant now);
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update BookingNotification n set n.nextAttemptAt = :now where n.deliveredAt is null")
    int retryPending(@org.springframework.data.repository.query.Param("now") Instant now);
}
